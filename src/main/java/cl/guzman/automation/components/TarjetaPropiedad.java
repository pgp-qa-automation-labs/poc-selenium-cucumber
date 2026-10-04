package cl.guzman.automation.components;

import cl.guzman.automation.model.Propiedad;
import cl.guzman.automation.utils.TextUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

/**
 * Tarjeta de una propiedad dentro del listado. Los locators son relativos a la raíz de la tarjeta.
 */
public class TarjetaPropiedad {

    private static final By NOMBRE = By.cssSelector(".tarjeta-nombre");
    private static final By UBICACION = By.cssSelector(".tarjeta-ubicacion");
    private static final By PRECIO = By.cssSelector(".tarjeta-precio");
    private static final By PRECIO_CLP = By.cssSelector(".tarjeta-precio-clp");
    private static final By CODIGO = By.cssSelector(".tarjeta-codigo");

    private final WebElement raiz;

    public TarjetaPropiedad(WebElement raiz) {
        this.raiz = raiz;
    }

    public String id() {
        return raiz.getDomAttribute("data-propiedad-id");
    }

    public String nombre() {
        return TextUtils.limpiar(raiz.findElement(NOMBRE).getText());
    }

    public String ubicacion() {
        return TextUtils.limpiar(raiz.findElement(UBICACION).getText());
    }

    /**
     * Precio principal (ej. "UF 5.798"), sin la conversión aproximada a CLP que viene anidada.
     */
    public String precio() {
        String completo = raiz.findElement(PRECIO).getText();
        String clp = raiz.findElements(PRECIO_CLP).stream().findFirst().map(WebElement::getText).orElse("");
        return TextUtils.limpiar(clp.isEmpty() ? completo : completo.replace(clp, ""));
    }

    public String codigo() {
        return TextUtils.limpiar(raiz.findElement(CODIGO).getText());
    }

    public WebElement elementoClickeable() {
        return raiz.findElement(NOMBRE);
    }

    public Propiedad toPropiedad() {
        return new Propiedad(id(), codigo(), nombre(), ubicacion(), precio());
    }
}
