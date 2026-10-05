package cl.guzman.automation.issues;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.triage.Diagnostico;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Punto de entrada de la etapa "Issues de triage" del pipeline: publica los diagnósticos que dejó la etapa
 * de pruebas (triage-report.json), sin volver a ejecutar las pruebas.
 *
 * <pre>mvn exec:java -Dexec.mainClass=cl.guzman.automation.issues.PublicarIssues -Dexec.args=ruta/triage-report.json</pre>
 */
public final class PublicarIssues {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PublicarIssues() {
    }

    public static void main(String[] args) throws IOException {
        Path reporte = Path.of(args.length > 0 ? args[0] : "target/triage/triage-report.json");
        if (!Files.exists(reporte)) {
            System.out.println("No hay diagnósticos de triage en " + reporte + ": nada que publicar.");
            return;
        }
        List<Diagnostico> diagnosticos = MAPPER.readValue(reporte.toFile(), new TypeReference<>() {
        });
        List<ResultadoIssue> resultados = IssuePublisher.publicar(diagnosticos, ConfigReader.get(), System.getenv());
        if (resultados.stream().anyMatch(r -> r.accion() == ResultadoIssue.Accion.ERROR)) {
            // exec:java corre en la JVM de Maven: una excepción hace fallar el paso sin cortar Maven abruptamente
            throw new IllegalStateException("Uno o más issues no se pudieron publicar; revisa target/issues/issues-report.md");
        }
    }
}
