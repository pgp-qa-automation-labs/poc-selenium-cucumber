package cl.guzman.automation.triage;

import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.utils.CiLog;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonSchemaLocalValidation;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.helpers.BetaToolRunner;
import com.anthropic.models.beta.messages.BetaBase64ImageSource;
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral;
import com.anthropic.models.beta.messages.BetaContentBlockParam;
import com.anthropic.models.beta.messages.BetaImageBlockParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.ToolRunnerCreateParams;
import cl.guzman.automation.evidencia.PaqueteEvidencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Agente que investiga por qué falló un escenario a partir de su paquete de evidencia. Parte de la captura y del error,
 * y decide por su cuenta qué más revisar (HTML, consola y red del navegador, API del backend) hasta registrar un diagnóstico.
 * No necesita el navegador: puede ejecutarse en una etapa separada del pipeline.
 */
public class TriageAgent {

    private static final Logger LOG = LoggerFactory.getLogger(TriageAgent.class);
    private static final long MAX_TOKENS = 16_000L;

    private static final String SYSTEM_PROMPT = """
            Eres el agente de triage de una suite de pruebas E2E (Selenium + Cucumber) de un sitio de corretaje de propiedades.
            Un escenario acaba de fallar. Tu objetivo es determinar la causa más probable del fallo con evidencia concreta,
            para que el equipo sepa a quién corresponde y qué hacer.

            Recibirás el escenario, los pasos ejecutados, el error del paso fallido, los intentos de self-healing
            (reparación automática de locators con IA) y una captura de pantalla del navegador en el momento del fallo.
            Con las herramientas puedes revisar la evidencia guardada en ese momento (HTML, consola y red del navegador)
            y consultar la API del backend ahora. Si otros escenarios fallaron con la misma evidencia, se te indican:
            el diagnóstico debe explicar la causa común.

            Cómo investigar:
            - Empieza por lo que ya tienes (error y captura). Usa las herramientas solo cuando aporten evidencia nueva.
            - Si a la página le faltan datos, revisa las peticiones de red del navegador: muestran lo que el navegador
              recibió en el momento del fallo. Una consulta posterior a la API puede responder bien aunque la falla haya
              sido real (fallas intermitentes, dependencias externas lentas, bloqueos de red), y el front puede ocultar
              esos errores sin escribir nada en la consola.
            - Usa la consulta directa a la API para saber si el backend responde AHORA; contrástala con la red del navegador.
            - Si el self-healing rechazó una reparación, su razón es una pista fuerte sobre un cambio de la UI.
            - Distingue con cuidado entre un defecto de la aplicación y un problema de la automatización.
            - No inventes hechos: cada evidencia debe venir de lo que viste o de lo que devolvió una herramienta.

            Termina SIEMPRE llamando una única vez a la herramienta RegistrarDiagnostico, en español.
            Si la evidencia no alcanza para concluir, usa la categoría INDETERMINADO y explica qué faltó.
            """;

    private final AnthropicClient client;
    private final EnvironmentConfig config;

    public TriageAgent(EnvironmentConfig config) {
        this.config = config;
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(config.secrets().anthropicApiKey())
                .timeout(Duration.ofSeconds(config.triage().requestTimeoutSeconds()))
                .maxRetries(2)
                .build();
    }

    /**
     * Investiga un grupo de fallos con la misma causa usando la evidencia del primero.
     * Nunca lanza excepciones: si el análisis falla, devuelve vacío y lo registra en el log.
     *
     * @param escenariosAfectados todos los escenarios del grupo
     * @param huella              identificador estable del grupo (no depende de la IA)
     */
    public Optional<Diagnostico> analizar(PaqueteEvidencia evidencia, List<String> escenariosAfectados, String huella) {
        FalloEscenario fallo = evidencia.fallo();
        String modelo = config.triage().model();
        CiLog.abrirGrupo("🔎 Triage IA: " + fallo.escenario()
                + (escenariosAfectados.size() > 1 ? " (+" + (escenariosAfectados.size() - 1) + " con la misma evidencia)" : ""));
        LOG.info("Triage: analizando el fallo de '{}' con {}...", fallo.escenario(), modelo);
        TriageContext.iniciar(evidencia, config);
        try {
            byte[] capturaPng = Files.exists(evidencia.captura()) ? Files.readAllBytes(evidencia.captura()) : null;
            List<BetaContentBlockParam> contenido = new ArrayList<>();
            contenido.add(BetaContentBlockParam.ofText(describir(fallo, escenariosAfectados)));
            if (capturaPng != null && capturaPng.length > 0) {
                contenido.add(BetaContentBlockParam.ofImage(BetaImageBlockParam.builder()
                        .source(BetaBase64ImageSource.builder()
                                .mediaType(BetaBase64ImageSource.MediaType.IMAGE_PNG)
                                .data(Base64.getEncoder().encodeToString(capturaPng))
                                .build())
                        .build()));
            }

            MessageCreateParams params = MessageCreateParams.builder()
                    .model(modelo)
                    .maxTokens(MAX_TOKENS)
                    .system(SYSTEM_PROMPT)
                    // Sin parámetros: su schema vacío no pasa la validación local del SDK, pero es válido para la API
                    .addTool(LeerHtml.class, JsonSchemaLocalValidation.NO)
                    .addTool(LeerConsola.class, JsonSchemaLocalValidation.NO)
                    .addTool(LeerRed.class, JsonSchemaLocalValidation.NO)
                    .addTool(ConsultarApi.class)
                    .addTool(RegistrarDiagnostico.class)
                    .addUserMessageOfBetaContentBlockParams(contenido)
                    // Caché automática: cada vuelta del agente reenvía el historial; así se cobra a tarifa reducida
                    .cacheControl(BetaCacheControlEphemeral.builder().build())
                    .build();

            BetaToolRunner runner = client.beta().messages().toolRunner(ToolRunnerCreateParams.builder()
                    .initialMessageParams(params)
                    .maxIterations(config.triage().maxIterations())
                    .build());

            int iteraciones = 0;
            long entrada = 0;
            long salida = 0;
            for (BetaMessage mensaje : runner) {
                iteraciones++;
                entrada += mensaje.usage().inputTokens()
                        + mensaje.usage().cacheReadInputTokens().orElse(0L)
                        + mensaje.usage().cacheCreationInputTokens().orElse(0L);
                salida += mensaje.usage().outputTokens();
            }

            RegistrarDiagnostico registrado = TriageContext.actual().diagnostico();
            if (registrado == null) {
                LOG.warn("Triage: el agente terminó sin registrar un diagnóstico ({} iteraciones)", iteraciones);
                return Optional.empty();
            }
            Diagnostico diagnostico = new Diagnostico(Instant.now(), huella, List.copyOf(escenariosAfectados), null, null,
                    fallo.escenario(), fallo.feature(), fallo.pasoFallido(), fallo.url(), fallo.ambiente(), fallo.simulado(), registrado.categoria, registrado.severidad, registrado.areaResponsable,
                    registrado.titulo, registrado.causaProbable,
                    registrado.evidencias == null ? List.of() : List.copyOf(registrado.evidencias),
                    registrado.accionRecomendada, registrado.confianza, modelo, iteraciones, entrada, salida);
            LOG.warn("Triage: {} ({}%) - {}", diagnostico.categoria(), diagnostico.confianza(), diagnostico.titulo());
            return Optional.of(diagnostico);
        } catch (IOException e) {
            LOG.error("Triage: no se pudo leer la captura de {}: {}", evidencia.directorio(), e.getMessage());
        } catch (AnthropicServiceException e) {
            LOG.error("Triage: error de la API de Anthropic ({}): {}", e.statusCode(), e.getMessage());
        } catch (AnthropicIoException e) {
            LOG.error("Triage: sin conexión con la API de Anthropic: {}", e.getMessage());
        } catch (RuntimeException e) {
            LOG.error("Triage: el análisis falló: {}", e.getMessage(), e);
        } finally {
            TriageContext.limpiar();
            CiLog.cerrarGrupo();
        }
        return Optional.empty();
    }

    private String describir(FalloEscenario fallo, List<String> escenariosAfectados) {
        String pasos = fallo.pasos().stream()
                .map(p -> "- [" + p.estado() + "] " + p.texto())
                .collect(Collectors.joining("\n"));
        return """
                <escenario>
                Nombre: %s
                Feature: %s
                Tags: %s
                Ambiente: %s (front %s, API %s)
                URL al fallar: %s
                Título de la página: %s
                </escenario>

                <pasos>
                %s
                </pasos>

                <paso_fallido>%s</paso_fallido>

                <error>
                %s
                </error>

                <self_healing>
                %s
                </self_healing>

                <otros_escenarios_con_la_misma_evidencia>
                %s
                </otros_escenarios_con_la_misma_evidencia>

                Se adjunta la captura de pantalla del navegador al momento del fallo.
                """.formatted(
                fallo.escenario(), fallo.feature(), String.join(" ", fallo.tags()),
                fallo.ambiente(), config.app().baseUrl(), config.app().apiUrl(),
                fallo.url(), fallo.tituloPagina(),
                pasos, fallo.pasoFallido(), fallo.error(),
                fallo.reparacionesHealing().isBlank() ? "Sin intentos de self-healing en este escenario." : fallo.reparacionesHealing(),
                escenariosAfectados.size() > 1
                        ? escenariosAfectados.stream().skip(1).map(e -> "- " + e).collect(Collectors.joining("\n"))
                        : "Ninguno.");
    }
}
