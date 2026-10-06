package cl.guzman.automation.issues;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.healing.Reparacion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Punto de entrada de la etapa "Tarea en &lt;gestor&gt;" del pipeline: por cada locator que el self-healing reparó por un
 * cambio real del sitio, crea (o comenta) una tarea de mantenimiento con el enlace al PR de corrección.
 *
 * <pre>mvn exec:java -Dexec.mainClass=cl.guzman.automation.issues.PublicarTareas -Dexec.args=ruta/healing-report.json</pre>
 *
 * La variable de entorno PR_URL indica el PR que corrige los locators.
 */
public final class PublicarTareas {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private PublicarTareas() {
    }

    public static void main(String[] args) throws IOException {
        Path reporte = Path.of(args.length > 0 ? args[0] : "target/healing/healing-report.json");
        if (!Files.exists(reporte)) {
            System.out.println("No hay reporte de self-healing en " + reporte + ": no hay tareas que publicar.");
            return;
        }
        EnvironmentConfig config = ConfigReader.get();
        String prUrl = System.getenv("PR_URL");
        List<Reparacion> reparaciones = MAPPER.readValue(reporte.toFile(), new TypeReference<>() {
        });
        // Solo reparaciones reales y aplicadas; una tarea por locator aunque se haya reparado en varios escenarios
        Map<String, TareaReparacion> tareas = new LinkedHashMap<>();
        reparaciones.stream()
                .filter(r -> r.aplicada() && !r.simulada())
                .map(r -> TareaReparacion.desde(r, config.env(), prUrl == null || prUrl.isBlank() ? null : prUrl))
                .forEach(t -> tareas.putIfAbsent(t.huella(), t));
        if (tareas.isEmpty()) {
            System.out.println("No hubo reparaciones reales del self-healing: no hay tareas que publicar.");
            return;
        }
        List<ResultadoIssue> resultados = IssuePublisher.publicarTareas(List.copyOf(tareas.values()), config, System.getenv());
        if (resultados.stream().anyMatch(r -> r.accion() == ResultadoIssue.Accion.ERROR)) {
            throw new IllegalStateException("Una o más tareas no se pudieron publicar; revisa target/issues/");
        }
    }
}
