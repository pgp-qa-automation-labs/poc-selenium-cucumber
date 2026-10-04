package cl.guzman.automation.pages;

import cl.guzman.automation.config.ConfigReader;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Home con el buscador principal (hero): operación, tipo, región, comuna y Buscar.
 */
public class HomePage extends BasePage {

    private static final By BUSCADOR = By.cssSelector(".buscador-hero");
    private static final String BOTON_OPERACION = "//div[contains(@class,'buscador-hero__pills')]/button[normalize-space()='%s']";
    private static final By SELECT_TIPO = By.cssSelector(".buscador-hero select[name='tipoPropiedad']");
    private static final By SELECT_REGION = By.cssSelector(".buscador-hero select[name='region']");
    private static final By SELECT_COMUNA = By.cssSelector(".buscador-hero select[name='comuna']");
    private static final By BOTON_BUSCAR = By.cssSelector(".buscador-hero__btn");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage abrir() {
        driver.get(ConfigReader.get().app().baseUrl());
        esperarVisible(BUSCADOR);
        return this;
    }

    public HomePage seleccionarOperacion(String operacion) {
        click(By.xpath(String.format(BOTON_OPERACION, operacion)));
        return this;
    }

    public HomePage seleccionarTipo(String tipo) {
        seleccionarPorTexto(SELECT_TIPO, tipo);
        return this;
    }

    public HomePage seleccionarRegion(String region) {
        seleccionarPorTexto(SELECT_REGION, region);
        return this;
    }

    public HomePage seleccionarComuna(String comuna) {
        seleccionarPorTexto(SELECT_COMUNA, comuna);
        return this;
    }

    public ResultadosPage buscar() {
        click(BOTON_BUSCAR);
        return new ResultadosPage(driver);
    }
}
