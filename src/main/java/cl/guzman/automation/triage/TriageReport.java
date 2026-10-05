package cl.guzman.automation.triage;

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
 * Acumula los diagnósticos de la ejecución y los escribe en target/triage/ al finalizar.
 */
public final class TriageReport {

    private static final Path DIRECTORIO = Path.of("target", "triage");
    private static final List<Diagnostico> DIAGNOSTICOS = Collections.synchronizedList(new ArrayList<>());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TriageReport() {
    }

    public static void registrar(Diagnostico diagnostico) {
        DIAGNOSTICOS.add(diagnostico);
    }

    public static String aMarkdown(Diagnostico d) {
        StringBuilder md = new StringBuilder()
                .append("## 🔎 ").append(d.titulo()).append("\n\n")
                .append("| | |\n|---|---|\n")
                .append("| Escenario | ").append(d.escenario()).append(" |\n")
                .append("| Paso fallido | ").append(d.pasoFallido()).append(" |\n")
                .append("| Categoría | **").append(d.categoria()).append("** |\n")
                .append("| Severidad | ").append(d.severidad()).append(" |\n")
                .append("| Área responsable | ").append(d.areaResponsable()).append(" |\n")
                .append("| Confianza | ").append(d.confianza()).append("% |\n")
                .append("| URL | ").append(d.url()).append(" |\n")
                .append("| Análisis | ").append(d.modelo()).append(", ").append(d.iteraciones()).append(" iteraciones, ")
                .append(d.tokensEntrada()).append(" tokens entrada / ").append(d.tokensSalida()).append(" salida |\n\n")
                .append("**Causa probable:** ").append(d.causaProbable()).append("\n\n")
                .append("**Evidencias:**\n");
        d.evidencias().forEach(e -> md.append("- ").append(e).append("\n"));
        md.append("\n**Acción recomendada:** ").append(d.accionRecomendada()).append("\n\n");
        return md.toString();
    }

    /**
     * Escribe target/triage/triage-report.{json,md} si hubo diagnósticos.
     */
    public static void escribirArchivos() {
        List<Diagnostico> diagnosticos;
        synchronized (DIAGNOSTICOS) {
            diagnosticos = List.copyOf(DIAGNOSTICOS);
        }
        if (diagnosticos.isEmpty()) {
            return;
        }
        StringBuilder md = new StringBuilder("# Triage de fallos\n\n");
        diagnosticos.forEach(d -> md.append(aMarkdown(d)));
        try {
            Files.createDirectories(DIRECTORIO);
            Files.writeString(DIRECTORIO.resolve("triage-report.json"), MAPPER.writeValueAsString(diagnosticos), StandardCharsets.UTF_8);
            Files.writeString(DIRECTORIO.resolve("triage-report.md"), md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir el reporte de triage en " + DIRECTORIO, e);
        }
    }
}
