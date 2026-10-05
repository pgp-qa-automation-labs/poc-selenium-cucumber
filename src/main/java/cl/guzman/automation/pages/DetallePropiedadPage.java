package cl.guzman.automation.pages;

import cl.guzman.automation.healing.Locator;
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

    private static final Locator TITULO = Locator.of(
            By.cssSelector("h2.detalles-titulo"),
            "Título (nombre) de la propiedad en la página de detalle");
    private static final Locator UBICACION = Locator.of(
            By.cssSelector("p.detalles-ubicacion"),
            "Dirección/ubicación de la propiedad en la página de detalle");
    private static final Locator CODIGO = Locator.of(
            By.cssSelector("span.detalles-codigo"),
            "Código de referencia de la propiedad (ej. 'Ref: GUZ-048') en la página de detalle");
    // Existen dos precios (versión mobile y desktop); solo uno es visible según el ancho de la ventana
    private static final Locator PRECIOS = Locator.ofMany(
            By.cssSelector("span.detalles-precio"),
            "Precio principal de la propiedad (ej. 'UF 5.798') en la página de detalle; existe una versión mobile y otra desktop");
    private static final Locator CARACTERISTICAS = Locator.ofMany(
            By.cssSelector(".detalles-item"),
            "Ítems de características de la propiedad (dormitorios, baños, superficies), cada uno con etiqueta y valor");
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
        return conHealing(PRECIOS, by -> {
            wait.until(d -> d.findElements(by).stream().anyMatch(WebElement::isDisplayed));
            return driver.findElements(by).stream()
                    .filter(WebElement::isDisplayed)
                    .map(WebElement::getText)
                    .map(TextUtils::limpiar)
                    .filter(texto -> !texto.isEmpty())
                    .findFirst()
                    .orElse("");
        });
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
