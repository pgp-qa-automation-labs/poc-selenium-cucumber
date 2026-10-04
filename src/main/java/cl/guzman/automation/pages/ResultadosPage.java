package cl.guzman.automation.pages;

import cl.guzman.automation.components.TarjetaPropiedad;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Listado de propiedades resultante de una búsqueda (ej. /EnVenta?tipo=...).
 */
public class ResultadosPage extends BasePage {

    private static final By TITULO = By.cssSelector("h4.pag-titulo");
    private static final By CONTEO = By.cssSelector("p.pag-conteo");
    private static final By TARJETAS = By.cssSelector(".tarjeta-propiedad[data-propiedad-id]");
    private static final Pattern NUMERO = Pattern.compile("(\\d+)");

    public ResultadosPage(WebDriver driver) {
        super(driver);
        esperarVisible(TITULO);
    }

    public String obtenerTitulo() {
        return obtenerTexto(TITULO);
    }

    public int obtenerConteo() {
        Matcher matcher = NUMERO.matcher(obtenerTexto(CONTEO));
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /**
     * Espera a que se rendericen las tarjetas. Si la búsqueda no trae resultados devuelve una lista vacía
     * para que el step lo valide con un mensaje claro en vez de un timeout.
     */
    public List<TarjetaPropiedad> obtenerTarjetas() {
        try {
            wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(TARJETAS, 0));
        } catch (TimeoutException e) {
            return List.of();
        }
        return driver.findElements(TARJETAS).stream()
                .map(TarjetaPropiedad::new)
                .toList();
    }

    public TarjetaPropiedad obtenerPrimeraTarjeta() {
        List<TarjetaPropiedad> tarjetas = obtenerTarjetas();
        if (tarjetas.isEmpty()) {
            throw new IllegalStateException("El listado no tiene propiedades para seleccionar");
        }
        return tarjetas.get(0);
    }

    public DetallePropiedadPage abrirDetalle(TarjetaPropiedad tarjeta) {
        // Se lee antes del click: al navegar, la tarjeta deja de existir en el DOM
        String id = tarjeta.id();
        scrollAlCentro(tarjeta.elementoClickeable());
        wait.until(ExpectedConditions.elementToBeClickable(tarjeta.elementoClickeable())).click();
        esperarUrlContiene("/propiedad/" + id);
        return new DetallePropiedadPage(driver);
    }
}
