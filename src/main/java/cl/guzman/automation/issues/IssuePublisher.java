package cl.guzman.automation.issues;

import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.triage.Diagnostico;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Publica los diagnósticos de la ejecución en el gestor configurado y deja en target/issues/ el detalle de
 * cada publicación, incluido el payload exacto enviado (o que se habría enviado en dry-run).
 */
public final class IssuePublisher {

    private static final Logger LOG = LoggerFactory.getLogger(IssuePublisher.class);
    private static final Path DIRECTORIO = Path.of("target", "issues");

    private IssuePublisher() {
    }

    public static List<ResultadoIssue> publicar(List<Diagnostico> diagnosticos, EnvironmentConfig config, Map<String, String> env) {
        EnvironmentConfig.Issues cfg = config.issues();
        if (cfg == null || !cfg.enabled() || diagnosticos.isEmpty()) {
            return List.of();
        }
        IssueTracker tracker = crearTracker(cfg, config.secrets());
        ContextoEjecucion contexto = ContextoEjecucion.desde(env);
        LOG.info("Issues: publicando {} diagnóstico(s) en {}{}", diagnosticos.size(), tracker.nombre(), cfg.dryRun() ? " (dry-run)" : "");

        List<ResultadoIssue> resultados = new ArrayList<>();
        for (Diagnostico d : diagnosticos) {
            if (d.categoria() == Diagnostico.Categoria.INDETERMINADO || d.confianza() < cfg.minConfidence()) {
                resultados.add(new ResultadoIssue(ResultadoIssue.Accion.OMITIDO, Huella.de(d), d.titulo(), null,
                        "Diagnóstico " + d.categoria() + " con confianza " + d.confianza() + "% (mínimo " + cfg.minConfidence() + "%)",
                        null, null));
                continue;
            }
            resultados.add(tracker.publicar(d, contexto, cfg.dryRun()));
        }
        resultados.forEach(r -> LOG.info("Issues: {} - {} {}", r.accion(), r.titulo(), r.url() == null ? "" : r.url()));
        escribir(resultados, cfg.tracker().toLowerCase(Locale.ROOT), tracker.nombre(), cfg.dryRun());
        return resultados;
    }

    private static IssueTracker crearTracker(EnvironmentConfig.Issues cfg, EnvironmentConfig.Secrets secrets) {
        return switch (cfg.tracker().toLowerCase(Locale.ROOT)) {
            case "github" -> new GitHubIssueTracker(cfg.githubRepository(), secrets.githubToken());
            case "jira" -> new JiraIssueTracker(cfg, secrets);
            case "azuredevops" -> new AzureDevOpsIssueTracker(cfg, secrets);
            default -> throw new IllegalArgumentException("Gestor de issues no soportado: " + cfg.tracker()
                    + " (disponibles: github, jira, azuredevops)");
        };
    }

    /**
     * Cada gestor escribe en su propia carpeta (target/issues/<gestor>/) para poder publicar en varios en la misma ejecución.
     */
    private static void escribir(List<ResultadoIssue> resultados, String carpeta, String tracker, boolean dryRun) {
        Path directorio = DIRECTORIO.resolve(carpeta);
        StringBuilder md = new StringBuilder("# Issues · ").append(tracker)
                .append("\n\n").append(dryRun ? "**Dry-run:** no se envió nada; la vista previa y el payload quedan en los artefactos.\n\n" : "")
                .append("| Acción | Título | Enlace | Detalle |\n|---|---|---|---|\n");
        resultados.forEach(r -> md.append("| ").append(r.accion()).append(" | ").append(r.titulo())
                .append(" | ").append(r.url() == null ? "—" : r.url()).append(" | ").append(r.detalle()).append(" |\n"));
        try {
            Files.createDirectories(directorio);
            for (ResultadoIssue r : resultados) {
                if (r.payload() != null) {
                    Files.writeString(directorio.resolve("issue-" + r.huella() + ".json"), r.payload(), StandardCharsets.UTF_8);
                }
                if (r.vistaPrevia() != null) {
                    Files.writeString(directorio.resolve("issue-" + r.huella() + ".md"), r.vistaPrevia(), StandardCharsets.UTF_8);
                }
            }
            Files.writeString(directorio.resolve("issues-report.md"), md.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir el reporte de issues en " + directorio, e);
        }
    }
}
