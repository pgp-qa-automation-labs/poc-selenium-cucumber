package cl.guzman.automation.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.HasCdp;

import java.util.List;
import java.util.Map;

/**
 * Simula cambios en el front sin tocar el sitio real. Inyecta (vía Chrome DevTools Protocol) un script que se
 * ejecuta en cada documento y aplica los cambios continuamente con un MutationObserver, para que sobrevivan
 * a los re-renders de React y a la navegación.
 */
public final class UiChangeSimulator {

    /**
     * Refactor de clases CSS: los elementos siguen existiendo y cumplen la misma función.
     * El self-healing debería repararlos y el flujo debería pasar.
     */
    public static final Map<String, String> CAMBIOS_COSMETICOS = Map.of(
            "buscador-hero__btn", "hero-search__submit",
            "pag-titulo", "results-heading",
            "detalles-titulo", "property-detail__title",
            "detalles-ubicacion", "property-detail__address");

    /**
     * El botón Buscar desaparece de la vista: es un cambio que rompe el flujo funcional.
     * El self-healing NO debería repararlo y el escenario debería fallar.
     */
    public static final List<String> CAMBIOS_FUNCIONALES_OCULTAR = List.of(".buscador-hero__btn");

    private static final String SCRIPT = """
            (() => {
              const renombrar = %s;
              const ocultar = %s;
              const aplicar = () => {
                for (const [desde, hacia] of Object.entries(renombrar)) {
                  document.querySelectorAll('.' + CSS.escape(desde)).forEach(el => {
                    el.classList.remove(desde);
                    el.classList.add(hacia);
                  });
                }
                ocultar.forEach(sel => document.querySelectorAll(sel).forEach(el => { el.style.display = 'none'; }));
              };
              const iniciar = () => {
                aplicar();
                new MutationObserver(aplicar).observe(document.documentElement,
                    { subtree: true, childList: true, attributes: true, attributeFilter: ['class'] });
              };
              if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', iniciar);
              } else {
                iniciar();
              }
            })();
            """;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UiChangeSimulator() {
    }

    public static void aplicar(WebDriver driver, Map<String, String> renombrarClases, List<String> ocultarSelectores) {
        if (!(driver instanceof HasCdp cdp)) {
            throw new IllegalStateException("La simulación de cambios de UI requiere un navegador Chromium (Chrome/Edge)");
        }
        try {
            String fuente = SCRIPT.formatted(MAPPER.writeValueAsString(renombrarClases), MAPPER.writeValueAsString(ocultarSelectores));
            cdp.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", Map.of("source", fuente));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo preparar el script de simulación", e);
        }
    }
}
