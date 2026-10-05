package cl.guzman.automation.issues;

import java.util.Map;

/**
 * Datos de la ejecución que acompañan al issue (dónde y cuándo falló). En GitHub Actions se toman de las
 * variables que define el runner; en local quedan como "local".
 *
 * @param urlEjecucion enlace a la ejecución del pipeline (captura y reportes completos), o null en local
 */
public record ContextoEjecucion(String origen, String rama, String commit, String urlEjecucion) {

    public static ContextoEjecucion desde(Map<String, String> env) {
        String servidor = env.get("GITHUB_SERVER_URL");
        String repo = env.get("GITHUB_REPOSITORY");
        String runId = env.get("GITHUB_RUN_ID");
        if (servidor == null || repo == null || runId == null) {
            return new ContextoEjecucion("local", "local", "local", null);
        }
        String commit = env.getOrDefault("GITHUB_SHA", "");
        return new ContextoEjecucion(
                "GitHub Actions (" + env.getOrDefault("GITHUB_EVENT_NAME", "?") + ")",
                env.getOrDefault("GITHUB_HEAD_REF", "").isBlank() ? env.getOrDefault("GITHUB_REF_NAME", "?") : env.get("GITHUB_HEAD_REF"),
                commit.length() > 7 ? commit.substring(0, 7) : commit,
                servidor + "/" + repo + "/actions/runs/" + runId);
    }
}
