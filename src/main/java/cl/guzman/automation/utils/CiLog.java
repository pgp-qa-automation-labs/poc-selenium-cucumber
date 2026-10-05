package cl.guzman.automation.utils;

/**
 * Secciones plegables en el log de GitHub Actions (comandos ::group:: / ::endgroup::).
 * Fuera de GitHub Actions no escribe nada, para no ensuciar la consola local.
 */
public final class CiLog {

    private static final boolean EN_GITHUB_ACTIONS = "true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"));

    private CiLog() {
    }

    public static void abrirGrupo(String titulo) {
        if (EN_GITHUB_ACTIONS) {
            System.out.println("::group::" + titulo.replace('\n', ' '));
            System.out.flush();
        }
    }

    public static void cerrarGrupo() {
        if (EN_GITHUB_ACTIONS) {
            System.out.println("::endgroup::");
            System.out.flush();
        }
    }
}
