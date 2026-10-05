package cl.guzman.automation.triage;

import cl.guzman.automation.config.EnvironmentConfig;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Estado del análisis en curso, accesible para las herramientas del agente.
 * El SDK instancia las herramientas por reflexión (sin constructor con parámetros), por eso el contexto
 * se comparte por hilo en vez de inyectarse.
 */
final class TriageContext {

    private static final Logger LOG = LoggerFactory.getLogger("Triage");
    private static final ThreadLocal<TriageContext> ACTUAL = new ThreadLocal<>();

    final WebDriver driver;
    final EnvironmentConfig config;
    private RegistrarDiagnostico diagnostico;
    private int usosDeHerramientas;
    private List<String> peticionesDeRed;

    private TriageContext(WebDriver driver, EnvironmentConfig config) {
        this.driver = driver;
        this.config = config;
    }

    static void iniciar(WebDriver driver, EnvironmentConfig config) {
        ACTUAL.set(new TriageContext(driver, config));
    }

    static TriageContext actual() {
        TriageContext contexto = ACTUAL.get();
        if (contexto == null) {
            throw new IllegalStateException("No hay un análisis de triage en curso");
        }
        return contexto;
    }

    static void limpiar() {
        ACTUAL.remove();
    }

    /**
     * Deja constancia en el log de cada herramienta que usa el agente, para poder seguir su razonamiento.
     */
    void registrarUso(String herramienta, String resultado) {
        usosDeHerramientas++;
        LOG.info("  Herramienta {}: {} → {}", usosDeHerramientas, herramienta, resultado);
    }

    /**
     * El log de red del navegador se vacía al leerlo: se guarda la primera lectura por si el agente lo consulta de nuevo.
     */
    List<String> peticionesDeRed(Supplier<List<String>> lector) {
        if (peticionesDeRed == null) {
            peticionesDeRed = lector.get();
        }
        return peticionesDeRed;
    }

    void registrar(RegistrarDiagnostico diagnostico) {
        this.diagnostico = diagnostico;
    }

    RegistrarDiagnostico diagnostico() {
        return diagnostico;
    }
}
