package cl.guzman.automation.hooks;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.driver.DriverFactory;
import cl.guzman.automation.driver.DriverManager;
import cl.guzman.automation.evidencia.LineaBase;
import cl.guzman.automation.evidencia.RecolectorEvidencia;
import cl.guzman.automation.healing.HealingReport;
import cl.guzman.automation.healing.Reparacion;
import cl.guzman.automation.issues.IssuePublisher;
import cl.guzman.automation.listeners.PasosListener;
import cl.guzman.automation.simulation.UiChangeSimulator;
import cl.guzman.automation.triage.Diagnostico;
import cl.guzman.automation.triage.EvaluadorTriage;
import cl.guzman.automation.triage.FalloEscenario;
import cl.guzman.automation.triage.TriageReport;
import cl.guzman.automation.utils.ScreenshotUtils;
import cl.guzman.automation.utils.WarmUpUtils;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class Hooks {

    private static final Logger LOG = LoggerFactory.getLogger(Hooks.class);
    private static final Set<String> TAGS_SIMULACION =
            Set.of("@ui-cambiada", "@ui-rota", "@api-caida", "@uf-caida", "@intermitente");
    private static final Path FUENTES = Path.of("src", "main", "java");
    // Intentos por escenario (clave: archivo .feature + línea, igual en todos sus reintentos)
    private static final Map<String, Integer> INTENTOS = new ConcurrentHashMap<>();

    @BeforeAll
    public static void prepararAmbiente() {
        EnvironmentConfig config = ConfigReader.get();
        LOG.info("Ambiente: {} | baseUrl: {} | navegador: {} (headless={})",
                config.env(), config.app().baseUrl(), config.browser().name(), config.browser().headless());
        LOG.info("Self-healing: {}", config.healingActivo()
                ? "activo (" + config.healing().model() + ", confianza mínima " + config.healing().minConfidence() + "%)"
                : "inactivo" + (config.healing().enabled() ? " (falta ANTHROPIC_API_KEY)" : " (healing.enabled=false)"));
        LOG.info("Reintentos por escenario fallido: {} | Triage al finalizar: {}", config.retry().maxRetries(),
                config.triageActivo() ? "activo (" + config.triage().model() + ", máximo "
                        + config.triage().maxInvestigaciones() + " investigaciones)" : "inactivo (solo se guarda la evidencia)");

        // La evidencia y los reportes son de esta ejecución: se descartan los de ejecuciones anteriores
        Stream.of(RecolectorEvidencia.RAIZ, TriageReport.DIRECTORIO, Path.of("target", "issues")).forEach(Hooks::borrar);

        EnvironmentConfig.WarmUp warmUp = config.warmUp();
        if (warmUp.enabled()) {
            WarmUpUtils.esperarDisponibilidad(
                    config.app().apiUrl() + warmUp.healthPath(),
                    Duration.ofSeconds(warmUp.maxSeconds()),
                    Duration.ofSeconds(warmUp.pollSeconds()));
        }
    }

    @Before(order = 0)
    public void iniciarNavegador(Scenario scenario) {
        int intento = INTENTOS.merge(idEstable(scenario), 1, Integer::sum);
        if (intento > 1) {
            LOG.warn("Reintento {} del escenario '{}'", intento - 1, scenario.getName());
        }
        HealingReport.iniciarEscenario(esSimulado(scenario));
        DriverManager.setDriver(DriverFactory.create(ConfigReader.get()));
    }

    @Before(value = "@ui-cambiada", order = 1)
    public void simularCambiosCosmeticos() {
        LOG.info("Simulación: renombrando clases del front {}", UiChangeSimulator.CAMBIOS_COSMETICOS);
        UiChangeSimulator.aplicar(DriverManager.getDriver(), UiChangeSimulator.CAMBIOS_COSMETICOS, List.of());
    }

    @Before(value = "@ui-rota", order = 1)
    public void simularCambioFuncional() {
        LOG.info("Simulación: ocultando elementos del front {}", UiChangeSimulator.CAMBIOS_FUNCIONALES_OCULTAR);
        UiChangeSimulator.aplicar(DriverManager.getDriver(), Map.of(), UiChangeSimulator.CAMBIOS_FUNCIONALES_OCULTAR);
    }

    @Before(value = "@api-caida", order = 1)
    public void simularApiCaida() {
        bloquear("/api/properties*");
    }

    @Before(value = "@uf-caida", order = 1)
    public void simularUfNoDisponible() {
        bloquear("/api/uf*");
    }

    /**
     * Falla momentánea: la API de propiedades no responde solo en el primer intento (el reintento debe pasar).
     */
    @Before(value = "@intermitente", order = 1)
    public void simularFallaMomentanea(Scenario scenario) {
        if (INTENTOS.getOrDefault(idEstable(scenario), 1) == 1) {
            bloquear("/api/properties*");
        } else {
            LOG.info("Simulación: en el reintento la API ya responde");
        }
    }

    /**
     * Después de cada paso aprobado se registra el estado de la página: si el escenario termina aprobado,
     * pasa a ser la línea base con la que el triage compara futuros fallos.
     */
    @AfterStep
    public void registrarLineaBase(Scenario scenario) {
        List<FalloEscenario.Paso> pasos = PasosListener.actual().pasos();
        if (esSimulado(scenario) || pasos.isEmpty() || !"PASSED".equals(pasos.get(pasos.size() - 1).estado())
                || !DriverManager.hasDriver()) {
            return;
        }
        LineaBase.registrarPaso(DriverManager.getDriver(), pasos.stream().map(FalloEscenario.Paso::texto).toList());
    }

    @After
    public void cerrarNavegador(Scenario scenario) {
        try {
            if (scenario.isFailed() && DriverManager.hasDriver()) {
                byte[] captura = ScreenshotUtils.capturar(DriverManager.getDriver());
                scenario.attach(captura, "image/png", scenario.getName());
                guardarEvidencia(scenario, captura);
            }
            if (!scenario.isFailed() && !esSimulado(scenario)) {
                LineaBase.confirmar();
            } else {
                LineaBase.descartar();
            }
            if (!scenario.isFailed() && INTENTOS.getOrDefault(idEstable(scenario), 1) > 1) {
                RecolectorEvidencia.marcarInestable(scenario.getName(), idEstable(scenario));
                LOG.warn("Escenario inestable: '{}' falló y luego pasó en un reintento", scenario.getName());
                scenario.log("⚠️ Escenario inestable: falló y pasó en un reintento. No se investiga ni se crea un issue.");
            }
            adjuntarReparaciones(scenario);
        } finally {
            DriverManager.quitDriver();
        }
    }

    @AfterAll
    public static void finalizar() {
        EnvironmentConfig config = ConfigReader.get();
        HealingReport.escribirArchivos();
        if (config.healing().patchSources()) {
            int corregidos = HealingReport.corregirCodigo(FUENTES);
            LOG.warn("Self-healing: {} locator(es) corregido(s) en el código fuente", corregidos);
        }
        // En el pipeline el triage es una etapa aparte (triage.enabled=false aquí); en local se evalúa al terminar
        if (config.triage().enabled()) {
            List<Diagnostico> diagnosticos = EvaluadorTriage.evaluar(config, RecolectorEvidencia.RAIZ, null);
            IssuePublisher.publicar(diagnosticos, config, System.getenv(), EvaluadorTriage.CAPTURAS);
        }
    }

    /**
     * Guarda el paquete de evidencia con el navegador aún abierto en el estado del fallo (ver PaqueteEvidencia).
     */
    private static void guardarEvidencia(Scenario scenario, byte[] captura) {
        WebDriver driver = DriverManager.getDriver();
        PasosListener.Ejecucion ejecucion = PasosListener.actual();
        List<Reparacion> reparaciones = HealingReport.delEscenario();
        FalloEscenario fallo = new FalloEscenario(
                scenario.getName(),
                scenario.getUri().toString(),
                // Los tags de simulación delatarían la causa: el agente debe deducirla solo de la evidencia
                scenario.getSourceTagNames().stream()
                        .filter(tag -> !TAGS_SIMULACION.contains(tag) && !"@manual".equals(tag))
                        .toList(),
                ejecucion.pasos(),
                ejecucion.pasoFallido(),
                ejecucion.error(),
                ConfigReader.get().env(),
                reparaciones.isEmpty() ? "" : HealingReport.aMarkdown(reparaciones, false),
                esSimulado(scenario),
                driver.getCurrentUrl(),
                driver.getTitle(),
                INTENTOS.getOrDefault(idEstable(scenario), 1));
        Path directorio = RecolectorEvidencia.guardar(driver, idEstable(scenario), fallo, captura);
        List<String> pasosPrevios = ejecucion.pasos().stream()
                .takeWhile(p -> "PASSED".equals(p.estado()))
                .map(FalloEscenario.Paso::texto)
                .toList();
        boolean conLineaBase = LineaBase.copiarA(directorio, pasosPrevios);
        LOG.info("Evidencia del fallo guardada en {} ({})", directorio,
                conLineaBase ? "con línea base de la última ejecución exitosa" : "sin línea base previa");
    }

    /**
     * Identificador del escenario igual en todos sus reintentos (Cucumber genera un id nuevo en cada ejecución).
     */
    private static String idEstable(Scenario scenario) {
        return scenario.getUri() + ":" + scenario.getLine();
    }

    private static boolean esSimulado(Scenario scenario) {
        return scenario.getSourceTagNames().stream().anyMatch(TAGS_SIMULACION::contains);
    }

    private static void bloquear(String ruta) {
        String patron = ConfigReader.get().app().apiUrl() + ruta;
        LOG.info("Simulación: bloqueando en el navegador las peticiones a {}", patron);
        UiChangeSimulator.bloquearPeticiones(DriverManager.getDriver(), List.of(patron));
    }

    private static void adjuntarReparaciones(Scenario scenario) {
        List<Reparacion> reparaciones = HealingReport.delEscenario();
        if (reparaciones.isEmpty()) {
            return;
        }
        scenario.attach(HealingReport.aMarkdown(reparaciones).getBytes(StandardCharsets.UTF_8), "text/markdown", "Self-healing");
        long aplicadas = reparaciones.stream().filter(Reparacion::aplicada).count();
        LOG.warn("Escenario '{}': {} reparación(es) aplicada(s) de {} intento(s) de self-healing",
                scenario.getName(), aplicadas, reparaciones.size());
    }

    private static void borrar(Path directorio) {
        if (!Files.exists(directorio)) {
            return;
        }
        try (Stream<Path> archivos = Files.walk(directorio)) {
            for (Path archivo : archivos.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(archivo);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo limpiar " + directorio, e);
        }
    }
}
