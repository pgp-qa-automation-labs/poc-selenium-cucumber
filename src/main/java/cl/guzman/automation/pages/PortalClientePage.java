package cl.guzman.automation.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Inicio del portal de clientes (/cliente), donde llega el cliente después de iniciar sesión.
 */
public class PortalClientePage extends BasePage {

    private static final By SALUDO = By.cssSelector(".cp-saludo .cp-titulo");

    public PortalClientePage(WebDriver driver) {
        super(driver);
    }

    public PortalClientePage esperarCarga() {
        esperarUrlContiene("/cliente");
        esperarVisible(SALUDO);
        return this;
    }

    /**
     * Saludo del encabezado, ej. "¡Hola, Camila! 👋".
     */
    public String obtenerSaludo() {
        return obtenerTexto(SALUDO);
    }
}
