package cl.guzman.automation.triage;

import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.evidencia.AgrupadorFallos;
import cl.guzman.automation.evidencia.PaqueteEvidencia;
import cl.guzman.automation.evidencia.RecolectorEvidencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Evaluación de triage de una ejecución (etapa 3 del pipeline, o al final de `mvn test` en local):
 * <ol>
 *   <li>descarta los escenarios inestables (pasaron en un reintento),</li>
 *   <li>agrupa los fallos consistentes que comparten la misma evidencia,</li>
 *   <li>investiga con el agente un escenario por grupo, hasta el tope configurado,</li>
 *   <li>deja la captura de cada grupo en target/triage/capturas/&lt;huella&gt;_&lt;escenario&gt;.png.</li>
 * </ol>
 */
public final class EvaluadorTriage {

    public static final Path CAPTURAS = TriageReport.DIRECTORIO.resolve("capturas");
    private static final Logger LOG = LoggerFactory.getLogger(EvaluadorTriage.class);

    private EvaluadorTriage() {
    }

    /**
     * @param urlBaseCapturas URL pública donde quedarán las capturas (ej. la rama de evidencias), o null si no se publican
     */
    public static List<Diagnostico> evaluar(EnvironmentConfig config, Path raizEvidencia, String urlBaseCapturas) {
        limpiarResultadosAnteriores();
        List<PaqueteEvidencia> paquetes = RecolectorEvidencia.listar(raizEvidencia);
        paquetes.stream()
                .filter(p -> p.estado() == PaqueteEvidencia.Estado.INESTABLE)
                .forEach(p -> TriageReport.registrarInestable(p.fallo().escenario()));
        List<PaqueteEvidencia> fallidos = paquetes.stream()
                .filter(p -> p.estado() == PaqueteEvidencia.Estado.FALLIDO)
                .toList();

        Map<String, List<PaqueteEvidencia>> grupos = AgrupadorFallos.agrupar(fallidos);
        LOG.info("Triage: {} escenario(s) con evidencia: {} fallido(s) en {} grupo(s), {} inestable(s)",
                paquetes.size(), fallidos.size(), grupos.size(), paquetes.size() - fallidos.size());

        TriageAgent agente = config.triageActivo() ? new TriageAgent(config) : null;
        int investigados = 0;
        for (Map.Entry<String, List<PaqueteEvidencia>> grupo : grupos.entrySet()) {
            List<String> escenarios = grupo.getValue().stream().map(p -> p.fallo().escenario()).toList();
            if (agente == null) {
                TriageReport.registrarNoInvestigado(escenarios, "triage inactivo (falta ANTHROPIC_API_KEY o triage.enabled=false)");
                continue;
            }
            if (investigados >= config.triage().maxInvestigaciones()) {
                TriageReport.registrarNoInvestigado(escenarios,
                        "se alcanzó el tope de " + config.triage().maxInvestigaciones() + " investigaciones por ejecución");
                continue;
            }
            investigados++;
            PaqueteEvidencia representante = grupo.getValue().get(0);
            String huella = AgrupadorFallos.huella(grupo.getKey(), representante.fallo().ambiente());
            agente.analizar(representante, escenarios, huella)
                    .map(d -> conCaptura(d, representante, urlBaseCapturas))
                    .ifPresentOrElse(TriageReport::registrar,
                            () -> TriageReport.registrarNoInvestigado(escenarios, "el agente no entregó un diagnóstico"));
        }
        TriageReport.escribirArchivos();
        return TriageReport.deLaEjecucion();
    }

    /**
     * Nombre legible para archivos: "Buscar departamentos en Ñuñoa" queda como "buscar-departamentos-en-nunoa".
     */
    private static String slug(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.length() > 60 ? slug.substring(0, 60).replaceAll("-$", "") : slug;
    }

    /**
     * Los resultados son de esta evaluación: un triage-report.json anterior no debe publicarse por error.
     */
    private static void limpiarResultadosAnteriores() {
        if (!Files.exists(TriageReport.DIRECTORIO)) {
            return;
        }
        try (Stream<Path> archivos = Files.walk(TriageReport.DIRECTORIO)) {
            for (Path archivo : archivos.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(archivo);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo limpiar " + TriageReport.DIRECTORIO, e);
        }
    }

    private static Diagnostico conCaptura(Diagnostico diagnostico, PaqueteEvidencia evidencia, String urlBase) {
        if (!Files.exists(evidencia.captura())) {
            return diagnostico;
        }
        String archivo = diagnostico.huella() + "_" + slug(diagnostico.escenario()) + ".png";
        try {
            Files.createDirectories(CAPTURAS);
            Files.copy(evidencia.captura(), CAPTURAS.resolve(archivo), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo copiar la captura de " + evidencia.directorio(), e);
        }
        String url = urlBase == null || urlBase.isBlank() ? null : urlBase.replaceAll("/+$", "") + "/" + archivo;
        return diagnostico.conCaptura(archivo, url);
    }
}
