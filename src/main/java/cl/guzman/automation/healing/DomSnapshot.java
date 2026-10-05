package cl.guzman.automation.healing;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

/**
 * Serializa el DOM actual en un HTML reducido para la IA: sin scripts, estilos, SVG ni imágenes embebidas,
 * conservando solo los atributos útiles para identificar elementos. Los elementos ocultos se marcan
 * con data-oculto="true" y no se expanden, para que la IA no proponga elementos que el usuario no ve.
 */
public final class DomSnapshot {

    private static final String SCRIPT = """
            const SKIP = new Set(['script', 'style', 'noscript', 'svg', 'iframe', 'link', 'meta', 'template']);
            const KEEP = /^(id|class|name|type|role|href|placeholder|value|title|alt|for|disabled|aria-.*|data-.*)$/;
            const limpiar = v => {
              if (v.startsWith('data:')) return 'data:...';
              return v.length > 150 ? v.slice(0, 150) + '...' : v;
            };
            function ser(el) {
              const tag = el.tagName.toLowerCase();
              if (SKIP.has(tag)) return '';
              const cs = getComputedStyle(el);
              const oculto = cs.display === 'none' || cs.visibility === 'hidden';
              let attrs = '';
              for (const a of el.attributes) {
                if (KEEP.test(a.name)) attrs += ' ' + a.name + '="' + limpiar(a.value).replace(/"/g, '&quot;') + '"';
              }
              if (oculto) return '<' + tag + attrs + ' data-oculto="true"></' + tag + '>';
              let inner = '';
              for (const n of el.childNodes) {
                if (n.nodeType === 3) {
                  const t = n.textContent.replace(/\\s+/g, ' ').trim();
                  if (t) inner += t.length > 200 ? t.slice(0, 200) + '...' : t;
                } else if (n.nodeType === 1) {
                  inner += ser(n);
                }
              }
              return '<' + tag + attrs + '>' + inner + '</' + tag + '>';
            }
            return ser(document.body);
            """;

    private DomSnapshot() {
    }

    public static Resultado capturar(WebDriver driver, int maxCaracteres) {
        String html = (String) ((JavascriptExecutor) driver).executeScript(SCRIPT);
        if (html == null) {
            html = "";
        }
        boolean truncado = html.length() > maxCaracteres;
        return new Resultado(truncado ? html.substring(0, maxCaracteres) : html, html.length(), truncado);
    }

    /**
     * @param html           HTML reducido (posiblemente truncado)
     * @param largoOriginal  largo antes de truncar, para dejar constancia en el reporte
     * @param truncado       true si se superó el máximo configurado
     */
    public record Resultado(String html, int largoOriginal, boolean truncado) {
    }
}
