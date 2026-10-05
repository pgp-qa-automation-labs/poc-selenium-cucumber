package cl.guzman.automation.healing;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.config.EnvironmentConfig;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquesta el self-healing de un locator roto:
 * <ol>
 *   <li>captura el DOM actual,</li>
 *   <li>pide a la IA que identifique el elemento y clasifique el cambio,</li>
 *   <li>acepta solo cambios COSMETICOS con confianza suficiente,</li>
 *   <li>valida el selector propuesto en el navegador antes de usarlo,</li>
 *   <li>registra el resultado (aplicado o rechazado) en {@link HealingReport}.</li>
 * </ol>
 * Las reparaciones aceptadas se reutilizan durante el resto de la ejecución para no consultar a la IA dos veces.
 */
public final class HealingEngine {

    private static final Logger LOG = LoggerFactory.getLogger(HealingEngine.class);
    private static final Map<String, By> REPARADOS = new ConcurrentHashMap<>();
    private static volatile HealingEngine instance;

    private final EnvironmentConfig.Healing config;
    private final ClaudeLocatorAdvisor advisor;

    private HealingEngine(EnvironmentConfig config) {
        this.config = config.healing();
        this.advisor = config.healingActivo() ? new ClaudeLocatorAdvisor(config) : null;
    }

    public static HealingEngine get() {
        if (instance == null) {
            synchronized (HealingEngine.class) {
                if (instance == null) {
                    instance = new HealingEngine(ConfigReader.get());
                }
            }
        }
        return instance;
    }

    public boolean activo() {
        return advisor != null;
    }

    /**
     * Selector a usar para el locator: el reparado en esta ejecución si existe, si no el original.
     */
    public By efectivo(Locator locator) {
        return REPARADOS.getOrDefault(locator.clave(), locator.by());
    }

    public Optional<By> reparar(WebDriver driver, Locator locator, String pagina) {
        if (!activo()) {
            return Optional.empty();
        }
        LOG.warn("Self-healing: no se encontró '{}' con {}. Consultando a la IA ({})...",
                locator.descripcion(), locator.by(), advisor.modelo());

        DomSnapshot.Resultado dom = DomSnapshot.capturar(driver, config.maxDomChars());
        if (dom.truncado()) {
            LOG.warn("Self-healing: el DOM ({} caracteres) supera healing.maxDomChars={} y se envió truncado",
                    dom.largoOriginal(), config.maxDomChars());
        }

        ClaudeLocatorAdvisor.Respuesta respuesta;
        try {
            respuesta = advisor.sugerir(locator, pagina, driver.getCurrentUrl(), dom);
        } catch (UnauthorizedException e) {
            return rechazar(locator, pagina, null, 0, 0, "API key de Anthropic inválida o revocada (401)");
        } catch (RateLimitException e) {
            return rechazar(locator, pagina, null, 0, 0, "Límite de uso de la API alcanzado (429): " + e.getMessage());
        } catch (AnthropicServiceException e) {
            return rechazar(locator, pagina, null, 0, 0, "Error de la API de Anthropic (" + e.statusCode() + "): " + e.getMessage());
        } catch (AnthropicIoException e) {
            return rechazar(locator, pagina, null, 0, 0, "Sin conexión con la API de Anthropic: " + e.getMessage());
        } catch (RuntimeException e) {
            return rechazar(locator, pagina, null, 0, 0, "Respuesta inválida de la IA: " + e.getMessage());
        }

        SugerenciaLocator sugerencia = respuesta.sugerencia();
        long entrada = respuesta.tokensEntrada();
        long salida = respuesta.tokensSalida();

        if (sugerencia.tipoCambio() != SugerenciaLocator.TipoCambio.COSMETICO) {
            return rechazar(locator, pagina, sugerencia, entrada, salida,
                    "La IA clasificó el cambio como " + sugerencia.tipoCambio() + ": afecta el flujo funcional");
        }
        if (sugerencia.confianza() < config.minConfidence()) {
            return rechazar(locator, pagina, sugerencia, entrada, salida,
                    "Confianza " + sugerencia.confianza() + "% menor al mínimo configurado (" + config.minConfidence() + "%)");
        }

        By nuevo;
        try {
            nuevo = construirBy(sugerencia);
        } catch (IllegalArgumentException e) {
            return rechazar(locator, pagina, sugerencia, entrada, salida, e.getMessage());
        }

        String validacion = validarEnNavegador(driver, nuevo, locator.multiple());
        if (validacion != null) {
            return rechazar(locator, pagina, sugerencia, entrada, salida, validacion);
        }

        REPARADOS.put(locator.clave(), nuevo);
        HealingReport.registrar(new Reparacion(Instant.now(), pagina, locator.descripcion(), locator.by().toString(),
                nuevo.toString(), sugerencia.tipoCambio(), sugerencia.confianza(), sugerencia.razon(), true, null,
                advisor.modelo(), entrada, salida));
        LOG.warn("Self-healing: '{}' reparado {} -> {} (confianza {}%). {}",
                locator.descripcion(), locator.by(), nuevo, sugerencia.confianza(), sugerencia.razon());
        return Optional.of(nuevo);
    }

    private static By construirBy(SugerenciaLocator sugerencia) {
        String selector = sugerencia.selector() == null ? "" : sugerencia.selector().trim();
        if (selector.isEmpty() || sugerencia.estrategia() == null) {
            throw new IllegalArgumentException("La IA no entregó un selector utilizable");
        }
        return switch (sugerencia.estrategia()) {
            case CSS -> By.cssSelector(selector);
            case XPATH -> By.xpath(selector);
            case NINGUNA -> throw new IllegalArgumentException("La IA no entregó un selector utilizable");
        };
    }

    /**
     * La IA puede equivocarse: el selector solo se acepta si en el navegador encuentra lo esperado.
     *
     * @return null si es válido, o el motivo de rechazo
     */
    private static String validarEnNavegador(WebDriver driver, By by, boolean multiple) {
        List<WebElement> encontrados;
        try {
            encontrados = driver.findElements(by);
        } catch (RuntimeException e) {
            return "El selector propuesto no es válido en el navegador: " + e.getMessage();
        }
        long visibles = encontrados.stream().filter(WebElement::isDisplayed).count();
        if (visibles == 0) {
            return "El selector propuesto no encuentra elementos visibles (" + encontrados.size() + " en total)";
        }
        if (!multiple && visibles > 1) {
            return "El selector propuesto no es único: encuentra " + visibles + " elementos visibles";
        }
        return null;
    }

    private Optional<By> rechazar(Locator locator, String pagina, SugerenciaLocator sugerencia,
                                  long entrada, long salida, String motivo) {
        HealingReport.registrar(new Reparacion(Instant.now(), pagina, locator.descripcion(), locator.by().toString(),
                sugerencia == null || sugerencia.selector() == null || sugerencia.selector().isBlank() ? null : sugerencia.selector(),
                sugerencia == null ? null : sugerencia.tipoCambio(),
                sugerencia == null ? 0 : sugerencia.confianza(),
                sugerencia == null ? null : sugerencia.razon(),
                false, motivo, advisor.modelo(), entrada, salida));
        LOG.error("Self-healing: no se reparó '{}' ({}). Motivo: {}", locator.descripcion(), locator.by(), motivo);
        return Optional.empty();
    }
}
