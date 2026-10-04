package cl.guzman.automation.pages;

import cl.guzman.automation.model.Propiedad;
import cl.guzman.automation.utils.TextUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.Optional;

/**
 * Detalle de una propiedad (/propiedad/{id}).
 */
public class DetallePropiedadPage extends BasePage {

    private static final By TITULO = By.cssSelector("h2.detalles-titulo");
    private static final By UBICACION = By.cssSelector("p.detalles-ubicacion");
    private static final By CODIGO = By.cssSelector("span.detalles-codigo");
    // Existen dos precios (versión mobile y desktop); solo uno es visible según el ancho de la ventana
    private static final By PRECIOS = By.cssSelector("span.detalles-precio");
    private static final By CARACTERISTICAS = By.cssSelector(".detalles-item");
    private static final By CARACTERISTICA_LABEL = By.cssSelector(".detalles-item-label");
    private static final By CARACTERISTICA_VALOR = By.cssSelector(".detalles-item-valor");

    public DetallePropiedadPage(WebDriver driver) {
        super(driver);
        esperarVisible(TITULO);
    }

    public String obtenerTitulo() {
        return TextUtils.limpiar(obtenerTexto(TITULO));
    }

    public String obtenerUbicacion() {
        return TextUtils.limpiar(obtenerTexto(UBICACION));
    }

    public String obtenerCodigo() {
        return TextUtils.limpiar(obtenerTexto(CODIGO).replaceFirst("(?i)^ref:", ""));
    }

    public String obtenerPrecio() {
        wait.until(d -> d.findElements(PRECIOS).stream().anyMatch(WebElement::isDisplayed));
        return driver.findElements(PRECIOS).stream()
                .filter(WebElement::isDisplayed)
                .map(WebElement::getText)
                .map(TextUtils::limpiar)
                .filter(texto -> !texto.isEmpty())
                .findFirst()
                .orElse("");
    }

    /**
     * Valor de una característica por su etiqueta (ej. "Dormitorios", "Baños"), sin importar mayúsculas ni tildes.
     */
    public Optional<String> obtenerCaracteristica(String etiqueta) {
        String buscada = TextUtils.normalizar(etiqueta);
        return esperarVisibles(CARACTERISTICAS).stream()
                .filter(item -> TextUtils.normalizar(item.findElement(CARACTERISTICA_LABEL).getText()).equals(buscada))
                .map(item -> TextUtils.limpiar(item.findElement(CARACTERISTICA_VALOR).getText()))
                .findFirst();
    }

    public Propiedad obtenerPropiedad() {
        String id = driver.getCurrentUrl().replaceAll(".*/propiedad/([^/?#]+).*", "$1");
        return new Propiedad(id, obtenerCodigo(), obtenerTitulo(), obtenerUbicacion(), obtenerPrecio());
    }
}
