package cl.guzman.automation.healing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Acumula las reparaciones de la ejecución (global) y del escenario actual (por hilo),
 * y las escribe en target/healing/ al finalizar.
 */
public final class HealingReport {

    private static final Path DIRECTORIO = Path.of("target", "healing");
    private static final List<Reparacion> EJECUCION = Collections.synchronizedList(new ArrayList<>());
    private static final ThreadLocal<List<Reparacion>> ESCENARIO = ThreadLocal.withInitial(ArrayList::new);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private HealingReport() {
    }

    static void registrar(Reparacion reparacion) {
        EJECUCION.add(reparacion);
        ESCENARIO.get().add(reparacion);
    }

    public static void iniciarEscenario() {
        ESCENARIO.get().clear();
    }

    public static List<Reparacion> delEscenario() {
        return List.copyOf(ESCENARIO.get());
    }

    public static List<Reparacion> deLaEjecucion() {
        synchronized (EJECUCION) {
            return List.copyOf(EJECUCION);
        }
    }

    /**
     * Resumen legible de una lista de reparaciones (para adjuntar al reporte de Cucumber o al PR).
     */
    public static String aMarkdown(List<Reparacion> reparaciones) {
        StringBuilder md = new StringBuilder("# Self-healing\n\n");
        long aplicadas = reparaciones.stream().filter(Reparacion::aplicada).count();
        md.append("Reparaciones aplicadas: **").append(aplicadas).append("** de ").append(reparaciones.size()).append("\n\n");
        for (Reparacion r : reparaciones) {
            md.append("## ").append(r.aplicada() ? "✅ " : "❌ ").append(r.descripcion()).append("\n\n")
                    .append("| | |\n|---|---|\n")
                    .append("| Página | `").append(r.pagina()).append("` |\n")
                    .append("| Locator original | `").append(r.locatorOriginal()).append("` |\n")
                    .append("| Locator nuevo | ").append(r.locatorNuevo() == null ? "—" : "`" + r.locatorNuevo() + "`").append(" |\n")
                    .append("| Tipo de cambio | ").append(r.tipoCambio() == null ? "—" : r.tipoCambio()).append(" |\n")
                    .append("| Confianza | ").append(r.confianza()).append("% |\n")
                    .append("| Modelo | ").append(r.modelo()).append(" (").append(r.tokensEntrada()).append(" tokens entrada / ")
                    .append(r.tokensSalida()).append(" salida) |\n\n")
                    .append("**Razón:** ").append(r.razon() == null ? "—" : r.razon()).append("\n\n");
            if (!r.aplicada()) {
                md.append("**Motivo de rechazo:** ").append(r.motivoRechazo()).append("\n\n");
            }
        }
        return md.toString();
    }

    public static String aJson(List<Reparacion> reparaciones) {
        try {
            return MAPPER.writeValueAsString(reparaciones);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo serializar el reporte de healing", e);
        }
    }

    /**
     * Escribe target/healing/healing-report.{json,md} si hubo intentos de reparación.
     */
    public static void escribirArchivos() {
        List<Reparacion> reparaciones = deLaEjecucion();
        if (reparaciones.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(DIRECTORIO);
            Files.writeString(DIRECTORIO.resolve("healing-report.json"), aJson(reparaciones), StandardCharsets.UTF_8);
            Files.writeString(DIRECTORIO.resolve("healing-report.md"), aMarkdown(reparaciones), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir el reporte de healing en " + DIRECTORIO, e);
        }
    }
}
