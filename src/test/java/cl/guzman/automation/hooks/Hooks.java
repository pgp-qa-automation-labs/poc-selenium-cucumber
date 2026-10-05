package cl.guzman.automation.hooks;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.driver.DriverFactory;
import cl.guzman.automation.driver.DriverManager;
import cl.guzman.automation.healing.HealingReport;
import cl.guzman.automation.healing.Reparacion;
import cl.guzman.automation.listeners.PasosListener;
import cl.guzman.automation.simulation.UiChangeSimulator;
import cl.guzman.automation.triage.FalloEscenario;
import cl.guzman.automation.triage.TriageAgent;
import cl.guzman.automation.triage.TriageReport;
import cl.guzman.automation.utils.ScreenshotUtils;
import cl.guzman.automation.utils.WarmUpUtils;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Hooks {

    private static final Logger LOG = LoggerFactory.getLogger(Hooks.class);
    private static final Set<String> TAGS_SIMULACION = Set.of("@ui-cambiada", "@ui-rota", "@api-caida");
    private static final Path FUENTES = Path.of("src", "main", "java");

    @BeforeAll
    public static void prepararAmbiente() {
        EnvironmentConfig config = ConfigReader.get();
        LOG.info("Ambiente: {} | baseUrl: {} | navegador: {} (headless={})",
                config.env(), config.app().baseUrl(), config.browser().name(), config.browser().headless());
        LOG.info("Self-healing: {}", config.healingActivo()
                ? "activo (" + config.healing().model() + ", confianza mínima " + config.healing().minConfidence() + "%)"
                : "inactivo" + (config.healing().enabled() ? " (falta ANTHROPIC_API_KEY)" : " (healing.enabled=false)"));
        LOG.info("Triage de fallos: {}", config.triageActivo()
                ? "activo (" + config.triage().model() + ", máximo " + config.triage().maxIterations() + " iteraciones)"
                : "inactivo");

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
        boolean simulado = scenario.getSourceTagNames().stream().anyMatch(TAGS_SIMULACION::contains);
        HealingReport.iniciarEscenario(simulado);
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
        String patron = ConfigReader.get().app().apiUrl() + "/api/properties*";
        LOG.info("Simulación: bloqueando en el navegador las peticiones a {}", patron);
        UiChangeSimulator.bloquearPeticiones(DriverManager.getDriver(), List.of(patron));
    }

    @After
    public void cerrarNavegador(Scenario scenario) {
        try {
            if (scenario.isFailed() && DriverManager.hasDriver()) {
                byte[] captura = ScreenshotUtils.capturar(DriverManager.getDriver());
                scenario.attach(captura, "image/png", scenario.getName());
                analizarFallo(scenario, captura);
            }
            adjuntarReparaciones(scenario);
        } finally {
            DriverManager.quitDriver();
        }
    }

    @AfterAll
    public static void escribirReportes() {
        TriageReport.escribirArchivos();
        HealingReport.escribirArchivos();
        if (ConfigReader.get().healing().patchSources()) {
            int corregidos = HealingReport.corregirCodigo(FUENTES);
            LOG.warn("Self-healing: {} locator(es) corregido(s) en el código fuente", corregidos);
        }
    }

    /**
     * Con el navegador aún abierto en el estado del fallo, el agente de triage investiga la causa.
     * Un problema del triage nunca cambia el resultado del escenario.
     */
    private static void analizarFallo(Scenario scenario, byte[] captura) {
        EnvironmentConfig config = ConfigReader.get();
        if (!config.triageActivo()) {
            return;
        }
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
                config.env(),
                reparaciones.isEmpty() ? "" : HealingReport.aMarkdown(reparaciones));

        new TriageAgent(config).analizar(DriverManager.getDriver(), fallo, captura).ifPresent(diagnostico -> {
            TriageReport.registrar(diagnostico);
            scenario.attach(TriageReport.aMarkdown(diagnostico).getBytes(StandardCharsets.UTF_8), "text/markdown", "Triage IA");
        });
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
}
