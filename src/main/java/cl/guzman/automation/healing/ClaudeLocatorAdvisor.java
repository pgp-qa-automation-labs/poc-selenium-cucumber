package cl.guzman.automation.healing;

import cl.guzman.automation.config.EnvironmentConfig;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;

import java.time.Duration;
import java.util.Optional;

/**
 * Consulta a Claude cuál es el nuevo selector de un elemento que dejó de encontrarse.
 * La respuesta llega como JSON validado contra el schema de {@link SugerenciaLocator} (structured outputs).
 */
public class ClaudeLocatorAdvisor {

    private static final long MAX_TOKENS = 16_000L;

    private static final String SYSTEM_PROMPT = """
            Eres el motor de self-healing de una suite de pruebas E2E con Selenium.
            Un selector dejó de encontrar su elemento en la página. Recibirás el selector original, la descripción
            funcional del elemento y el HTML actual de la página, simplificado (sin scripts ni estilos; los elementos
            ocultos están marcados con data-oculto="true" y no se muestra su contenido).

            Tu tarea:
            1. Identificar en el HTML el elemento VISIBLE que cumple exactamente la función descrita.
            2. Clasificar el cambio:
               - COSMETICO: el elemento sigue existiendo, visible y con la misma función; solo cambió cómo se
                 identifica (clases renombradas, id distinto, reestructuración del HTML). El flujo funcional no se ve afectado.
               - FUNCIONAL: existe un elemento parecido, pero su propósito, texto o comportamiento ya no corresponde
                 a la descripción (por ejemplo, el botón ahora dice otra cosa con un significado distinto).
               - NO_ENCONTRADO: no hay un elemento visible que cumpla la función (fue eliminado u ocultado).
            3. Si es COSMETICO, proponer un selector robusto que exista en el HTML entregado:
               - Prefiere, en este orden: id, name, data-*, aria-*, texto visible estable, clases semánticas.
               - Evita índices posicionales (nth-child, [3]) y clases utilitarias de layout (col-*, mb-*, d-flex).
               - Si el elemento es un único elemento, el selector debe ser único en la página.
               - Si se indica que el selector apunta a VARIOS elementos (un listado), debe ubicar a todos los del mismo tipo.
               - Usa CSS cuando sea posible; XPath solo si necesitas ubicar por texto.

            Reglas:
            - Nunca inventes atributos o clases que no aparezcan en el HTML entregado.
            - Ante la duda entre COSMETICO y FUNCIONAL, elige FUNCIONAL: es preferible fallar una prueba que ocultar un error real.
            - La confianza (0-100) refleja qué tan seguro estás de que el selector apunta al MISMO elemento funcional.
            """;

    private final AnthropicClient client;
    private final String modelo;

    public ClaudeLocatorAdvisor(EnvironmentConfig config) {
        EnvironmentConfig.Healing healing = config.healing();
        this.modelo = healing.model();
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(config.secrets().anthropicApiKey())
                .timeout(Duration.ofSeconds(healing.requestTimeoutSeconds()))
                .maxRetries(2)
                .build();
    }

    public String modelo() {
        return modelo;
    }

    public Respuesta sugerir(Locator locator, String pagina, String url, DomSnapshot.Resultado dom) {
        StructuredMessageCreateParams<SugerenciaLocator> params = MessageCreateParams.builder()
                .model(modelo)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .outputConfig(SugerenciaLocator.class)
                .addUserMessage(construirMensaje(locator, pagina, url, dom))
                .build();

        StructuredMessage<SugerenciaLocator> respuesta = client.messages().create(params);

        Optional<StopReason> stopReason = respuesta.stopReason();
        if (stopReason.isPresent() && !StopReason.END_TURN.equals(stopReason.get())) {
            throw new IllegalStateException("La IA no completó la respuesta (stop_reason=" + stopReason.get() + ")");
        }

        SugerenciaLocator sugerencia = respuesta.content().stream()
                .flatMap(bloque -> bloque.text().stream())
                .map(texto -> texto.text())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("La IA no devolvió una sugerencia"));

        return new Respuesta(sugerencia, respuesta.usage().inputTokens(), respuesta.usage().outputTokens());
    }

    private static String construirMensaje(Locator locator, String pagina, String url, DomSnapshot.Resultado dom) {
        return """
                <elemento>
                Página (Page Object): %s
                URL actual: %s
                Descripción funcional: %s
                Selector original que dejó de funcionar: %s
                Apunta a: %s
                </elemento>

                <html_actual%s>
                %s
                </html_actual>
                """.formatted(
                pagina,
                url,
                locator.descripcion(),
                locator.by(),
                locator.multiple() ? "VARIOS elementos del mismo tipo (un listado)" : "UN único elemento",
                dom.truncado() ? " truncado=\"true\" largo_original=\"" + dom.largoOriginal() + "\"" : "",
                dom.html());
    }

    public record Respuesta(SugerenciaLocator sugerencia, long tokensEntrada, long tokensSalida) {
    }
}
