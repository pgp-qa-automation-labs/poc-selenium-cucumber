package cl.guzman.automation.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Arma la configuración de la ejecución. Los JSON contienen solo datos; toda la lógica está aquí.
 *
 * <p>Orden de precedencia (el último gana):
 * <ol>
 *   <li>config/config.json (defaults comunes)</li>
 *   <li>config/environments/env_&lt;env&gt;.json (solo lo que cambia en ese ambiente)</li>
 *   <li>reglas por contexto (CI=true fuerza headless)</li>
 *   <li>propiedades de sistema -Dseccion.clave=valor</li>
 * </ol>
 * El ambiente se elige con -Denv=dev|qa|prod (por defecto qa).
 */
public final class ConfigReader {

    private static final String DEFAULT_ENV = "qa";
    private static final String DEFAULTS_JSON = "config/config.json";
    private static final String ENV_JSON = "config/environments/env_%s.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static EnvironmentConfig instance;

    private ConfigReader() {
    }

    public static synchronized EnvironmentConfig get() {
        if (instance == null) {
            String env = System.getProperty("env", DEFAULT_ENV).trim().toLowerCase(Locale.ROOT);
            instance = load(env, System.getenv(), System.getProperties());
        }
        return instance;
    }

    /**
     * Construye la configuración a partir de las entradas recibidas, sin leer estado global (facilita probarla).
     */
    static EnvironmentConfig load(String env, Map<String, String> sysEnv, Properties sysProps) {
        ObjectNode config = readJson(DEFAULTS_JSON);
        merge(config, readJson(String.format(ENV_JSON, env)));

        if ("true".equalsIgnoreCase(sysEnv.get("CI"))) {
            seccion(config, "browser").put("headless", true);
        }

        aplicarOverrides(config, sysProps);
        normalizarUrls(seccion(config, "app"));
        validar(config, env);

        config.put("env", env);
        config.putObject("secrets")
                .put("anthropicApiKey", sysEnv.getOrDefault("ANTHROPIC_API_KEY", ""))
                .put("githubToken", sysEnv.getOrDefault("GITHUB_TOKEN", ""))
                .put("jiraEmail", sysEnv.getOrDefault("JIRA_EMAIL", ""))
                .put("jiraApiToken", sysEnv.getOrDefault("JIRA_API_TOKEN", ""))
                .put("azureDevOpsPat", sysEnv.getOrDefault("AZURE_DEVOPS_PAT", ""));

        try {
            return MAPPER.treeToValue(config, EnvironmentConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo mapear la configuración del ambiente '" + env + "'", e);
        }
    }

    /**
     * Mezcla profunda: los objetos se combinan campo a campo y los valores simples de {@code override} reemplazan.
     */
    private static void merge(ObjectNode base, JsonNode override) {
        for (Map.Entry<String, JsonNode> campo : override.properties()) {
            JsonNode actual = base.get(campo.getKey());
            if (actual instanceof ObjectNode objeto && campo.getValue().isObject()) {
                merge(objeto, campo.getValue());
            } else {
                base.set(campo.getKey(), campo.getValue());
            }
        }
    }

    /**
     * Aplica -Dseccion.clave=valor solo sobre claves que ya existen, convirtiendo al tipo del valor actual.
     */
    private static void aplicarOverrides(ObjectNode config, Properties sysProps) {
        for (String nombre : sysProps.stringPropertyNames()) {
            String[] partes = nombre.split("\\.");
            if (partes.length != 2 || !(config.get(partes[0]) instanceof ObjectNode seccion) || !seccion.has(partes[1])) {
                continue;
            }
            String clave = partes[1];
            String valor = sysProps.getProperty(nombre);
            JsonNode actual = seccion.get(clave);
            if (actual.isBoolean()) {
                seccion.put(clave, Boolean.parseBoolean(valor));
            } else if (actual.isNumber()) {
                seccion.put(clave, parsearEntero(nombre, valor));
            } else {
                seccion.put(clave, valor);
            }
        }
    }

    private static int parsearEntero(String nombre, String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("-D" + nombre + " debe ser un número entero y se recibió '" + valor + "'", e);
        }
    }

    private static void normalizarUrls(ObjectNode app) {
        String baseUrl = app.path("baseUrl").asText("");
        if (!baseUrl.isEmpty() && !baseUrl.endsWith("/")) {
            app.put("baseUrl", baseUrl + "/");
        }
        app.put("apiUrl", app.path("apiUrl").asText("").replaceAll("/+$", ""));
    }

    private static void validar(ObjectNode config, String env) {
        if (config.path("app").path("baseUrl").asText("").isBlank()) {
            throw new IllegalStateException("baseUrl no definida para el ambiente '" + env + "'");
        }
        if (config.path("warmUp").path("enabled").asBoolean() && config.path("app").path("apiUrl").asText("").isBlank()) {
            throw new IllegalStateException("warmUp habilitado pero apiUrl no definida para el ambiente '" + env + "'");
        }
    }

    private static ObjectNode seccion(ObjectNode config, String nombre) {
        return config.get(nombre) instanceof ObjectNode objeto ? objeto : config.putObject(nombre);
    }

    private static ObjectNode readJson(String path) {
        try (InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("No existe el recurso: " + path);
            }
            JsonNode node = MAPPER.readTree(is);
            if (!(node instanceof ObjectNode objeto)) {
                throw new IllegalStateException(path + " debe contener un objeto JSON");
            }
            return objeto;
        } catch (IOException e) {
            throw new IllegalStateException("Error leyendo " + path + ": " + e.getMessage(), e);
        }
    }
}
