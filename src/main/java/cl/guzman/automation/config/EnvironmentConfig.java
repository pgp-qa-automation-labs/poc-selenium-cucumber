package cl.guzman.automation.config;

/**
 * Configuración final de la ejecución, resultado de combinar config.json con env_&lt;ambiente&gt;.json (ver ConfigReader).
 */
public record EnvironmentConfig(
        String env,
        App app,
        Browser browser,
        Timeouts timeouts,
        WarmUp warmUp,
        Healing healing,
        Triage triage,
        Issues issues,
        Secrets secrets) {

    /**
     * El triage solo opera si está habilitado y hay una API key disponible.
     */
    public boolean triageActivo() {
        return triage != null && triage.enabled() && tieneApiKey();
    }

    private boolean tieneApiKey() {
        return secrets != null && secrets.anthropicApiKey() != null && !secrets.anthropicApiKey().isBlank();
    }

    /**
     * El healing solo opera si está habilitado y hay una API key disponible; sin key el framework funciona como siempre.
     */
    public boolean healingActivo() {
        return healing != null && healing.enabled() && tieneApiKey();
    }

    public record App(String baseUrl, String apiUrl) {
    }

    public record Browser(String name, boolean headless, int windowWidth, int windowHeight) {
    }

    public record Timeouts(int explicitWaitSeconds, int pageLoadSeconds) {
    }

    public record WarmUp(boolean enabled, int maxSeconds, int pollSeconds, String healthPath) {
    }

    /**
     * @param patchSources si es true, al terminar la ejecución se corrigen en el código los locators reparados
     *                     (lo activa el pipeline para luego abrir un PR; en local queda en false)
     */
    public record Healing(boolean enabled, String model, int minConfidence, int maxDomChars, int requestTimeoutSeconds,
                          boolean patchSources) {
    }

    /**
     * @param maxIterations máximo de vueltas del agente (cada vuelta es una llamada a la API)
     */
    public record Triage(boolean enabled, String model, int maxIterations, int requestTimeoutSeconds) {
    }

    /**
     * Publicación de los diagnósticos del triage como issues en un gestor de proyectos.
     *
     * @param tracker          gestor destino ("github"; más adelante "jira" y "azuredevops")
     * @param dryRun           true: no envía nada, escribe en target/issues/ el payload exacto que se enviaría
     * @param minConfidence    confianza mínima del diagnóstico para publicarlo
     * @param githubRepository repositorio destino en formato owner/repo
     */
    public record Issues(boolean enabled, String tracker, boolean dryRun, int minConfidence, String githubRepository) {
    }

    public record Secrets(String anthropicApiKey, String githubToken) {

        @Override
        public String toString() {
            return "Secrets[anthropicApiKey=" + enmascarar(anthropicApiKey) + ", githubToken=" + enmascarar(githubToken) + "]";
        }

        private static String enmascarar(String valor) {
            return valor == null || valor.isEmpty() ? "<vacía>" : "****";
        }
    }
}
