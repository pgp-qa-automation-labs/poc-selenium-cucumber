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
 * Resultado de la evaluación de triage de una ejecución: diagnósticos, fallos inestables y grupos no investigados.
 * Se escribe en target/triage/ (triage-report.json es el contrato con la etapa de publicación de issues).
 */
public final class TriageReport {

    public static final Path DIRECTORIO = Path.of("target", "triage");
    private static final List<Diagnostico> DIAGNOSTICOS = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> INESTABLES = Collections.synchronizedList(new ArrayList<>());
    private static final List<String> NO_INVESTIGADOS = Collections.synchronizedList(new ArrayList<>());
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TriageReport() {
    }

    public static void registrar(Diagnostico diagnostico) {
        DIAGNOSTICOS.add(diagnostico);
    }

    public static void registrarInestable(String escenario) {
        INESTABLES.add(escenario);
    }

    public static void registrarNoInvestigado(List<String> escenarios, String motivo) {
        NO_INVESTIGADOS.add(String.join(", ", escenarios) + " — " + motivo);
    }

    public static List<Diagnostico> deLaEjecucion() {
        synchronized (DIAGNOSTICOS) {
            return List.copyOf(DIAGNOSTICOS);
        }
    }

    public static String aMarkdown(Diagnostico d) {
        StringBuilder md = new StringBuilder()
                .append("## 🔎 ").append(d.titulo()).append("\n\n");
        if (d.capturaUrl() != null) {
            md.append("![Captura al fallar](").append(d.capturaUrl()).append(")\n\n");
        }
        md.append("| | |\n|---|---|\n")
                .append("| Escenario | ").append(d.escenario()).append(" |\n");
        if (d.escenariosAfectados().size() > 1) {
            md.append("| Escenarios afectados | ").append(d.escenariosAfectados().size()).append(": ")
                    .append(String.join("; ", d.escenariosAfectados())).append(" |\n");
        }
        md.append("| Paso fallido | ").append(d.pasoFallido()).append(" |\n")
                .append("| Categoría | **").append(d.categoria()).append("** |\n")
                .append("| Severidad | ").append(d.severidad()).append(" |\n")
                .append("| Área responsable | ").append(d.areaResponsable()).append(" |\n")
                .append("| Confianza | ").append(d.confianza()).append("% |\n")
                .append("| URL | ").append(d.url()).append(" |\n")
                .append("| Huella | `").append(d.huella()).append("` |\n")
                .append("| Análisis | ").append(d.modelo()).append(", ").append(d.iteraciones()).append(" iteraciones, ")
                .append(d.tokensEntrada()).append(" tokens entrada / ").append(d.tokensSalida()).append(" salida |\n\n")
                .append("**Causa probable:** ").append(d.causaProbable()).append("\n\n")
                .append("**Evidencias:**\n");
        d.evidencias().forEach(e -> md.append("- ").append(e).append("\n"));
        md.append("\n**Acción recomendada:** ").append(d.accionRecomendada()).append("\n\n");
        return md.toString();
    }

    /**
     * Escribe target/triage/triage-report.{json,md} si hubo algo que informar.
     */
    public static void escribirArchivos() {
        List<Diagnostico> diagnosticos = deLaEjecucion();
        List<String> inestables;
        List<String> noInvestigados;
        synchronized (INESTABLES) {
            inestables = List.copyOf(INESTABLES);
        }
        synchronized (NO_INVESTIGADOS) {
            noInvestigados = List.copyOf(NO_INVESTIGADOS);
        }
        if (diagnosticos.isEmpty() && inestables.isEmpty() && noInvestigados.isEmpty()) {
            return;
        }
        StringBuilder md = new StringBuilder("# Triage de fallos\n\n");
        diagnosticos.forEach(d -> md.append(aMarkdown(d)));
        if (!inestables.isEmpty()) {
            md.append("## ⚠️ Escenarios inestables\n\nFallaron y luego pasaron en un reintento: no se investigaron ni se crean issues.\n\n");
            inestables.forEach(e -> md.append("- ").append(e).append("\n"));
            md.append("\n");
        }
        if (!noInvestigados.isEmpty()) {
            md.append("## ⏭️ Fallos no investigados\n\n");
            noInvestigados.forEach(e -> md.append("- ").append(e).append("\n"));
            md.append("\n");
        }
        try {
            Files.createDirectories(DIRECTORIO);
            if (!diagnosticos.isEmpty()) {
                Files.writeString(DIRECTORIO.resolve("triage-report.json"), MAPPER.writeValueAsString(diagnosticos), StandardCharsets.UTF_8);
            }
            Files.writeString(DIRECTORIO.resolve("triage-report.md"), md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir el reporte de triage en " + DIRECTORIO, e);
        }
    }
}
