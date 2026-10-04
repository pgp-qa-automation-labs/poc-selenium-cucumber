package cl.guzman.automation.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Carga la configuración ejecutando config/config.js (GraalJS) sobre config/environments/env_&lt;env&gt;.json.
 * El ambiente se elige con -Denv=dev|qa|prod (por defecto qa).
 */
public final class ConfigReader {

    private static final String DEFAULT_ENV = "qa";
    private static final String CONFIG_JS = "config/config.js";
    private static final String ENV_JSON = "config/environments/env_%s.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static EnvironmentConfig instance;

    private ConfigReader() {
    }

    public static synchronized EnvironmentConfig get() {
        if (instance == null) {
            instance = load(System.getProperty("env", DEFAULT_ENV).trim().toLowerCase(Locale.ROOT));
        }
        return instance;
    }

    private static EnvironmentConfig load(String env) {
        String script = readResource(CONFIG_JS);
        String envJson = readResource(String.format(ENV_JSON, env));

        // El script solo recibe strings: no tiene acceso a clases Java ni al sistema
        try (Context context = Context.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            context.eval(Source.create("js", script));
            Value fn = context.getBindings("js").getMember("fn");
            String json = fn.execute(env, envJson, toJson(System.getenv()), toJson(systemProperties())).asString();
            return MAPPER.readValue(json, EnvironmentConfig.class);
        } catch (PolyglotException e) {
            throw new IllegalStateException("config.js falló para el ambiente '" + env + "': " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo mapear la configuración del ambiente '" + env + "'", e);
        }
    }

    private static Map<String, String> systemProperties() {
        return System.getProperties().stringPropertyNames().stream()
                .collect(Collectors.toMap(name -> name, System::getProperty));
    }

    private static String toJson(Map<String, String> map) throws JsonProcessingException {
        return MAPPER.writeValueAsString(map);
    }

    private static String readResource(String path) {
        try (InputStream is = ConfigReader.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("No existe el recurso: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Error leyendo " + path, e);
        }
    }
}
