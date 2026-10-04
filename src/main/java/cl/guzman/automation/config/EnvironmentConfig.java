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
        Secrets secrets) {

    public record App(String baseUrl, String apiUrl) {
    }

    public record Browser(String name, boolean headless, int windowWidth, int windowHeight) {
    }

    public record Timeouts(int explicitWaitSeconds, int pageLoadSeconds) {
    }

    public record WarmUp(boolean enabled, int maxSeconds, int pollSeconds, String healthPath) {
    }

    public record Secrets(String anthropicApiKey) {

        @Override
        public String toString() {
            return "Secrets[anthropicApiKey=" + (anthropicApiKey == null || anthropicApiKey.isEmpty() ? "<vacía>" : "****") + "]";
        }
    }
}
