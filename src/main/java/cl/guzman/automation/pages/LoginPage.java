package cl.guzman.automation.pages;

import cl.guzman.automation.config.ConfigReader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Login del portal: pestaña "Soy cliente" con usuario y contraseña.
 */
public class LoginPage extends BasePage {

    private static final By PESTANA_CLIENTE = By.xpath("//button[contains(@class,'login-tab')][normalize-space()='Soy cliente']");
    private static final By INPUT_USUARIO = By.cssSelector(".login-form input[autocomplete='username']");
    private static final By INPUT_PASSWORD = By.cssSelector(".login-form input[autocomplete='current-password']");
    private static final By BOTON_INGRESAR = By.cssSelector(".login-form .login-btn-submit");

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    public LoginPage abrir() {
        driver.get(ConfigReader.get().app().baseUrl() + "login");
        click(PESTANA_CLIENTE);
        esperarVisible(INPUT_USUARIO);
        return this;
    }

    public PortalClientePage iniciarSesion(String usuario, String password) {
        escribir(INPUT_USUARIO, usuario);
        escribir(INPUT_PASSWORD, password);
        click(BOTON_INGRESAR);
        return new PortalClientePage(driver);
    }
}
