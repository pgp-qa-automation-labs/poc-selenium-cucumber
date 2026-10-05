package cl.guzman.automation.pages;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.healing.Locator;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Home con el buscador principal (hero): operación, tipo, región, comuna y Buscar.
 */
public class HomePage extends BasePage {

    private static final Locator BUSCADOR = Locator.of(
            By.cssSelector(".buscador-hero"),
            "Contenedor del buscador principal de propiedades en el home");
    private static final String BOTON_OPERACION = "//div[contains(@class,'buscador-hero__pills')]/button[normalize-space()='%s']";
    private static final Locator SELECT_TIPO = Locator.of(
            By.cssSelector(".buscador-hero select[name='tipoPropiedad']"),
            "Selector de tipo de propiedad (Casa, Departamento, Terreno, Oficina) del buscador principal");
    private static final Locator SELECT_REGION = Locator.of(
            By.cssSelector(".buscador-hero select[name='region']"),
            "Selector de región del buscador principal");
    private static final Locator SELECT_COMUNA = Locator.of(
            By.cssSelector(".buscador-hero select[name='comuna']"),
            "Selector de comuna del buscador principal");
    private static final Locator BOTON_BUSCAR = Locator.of(
            By.cssSelector(".buscador-hero__bar > button.buscador-hero__btn"),
            "Botón BUSCAR que ejecuta la búsqueda del buscador principal del home");

    public HomePage(WebDriver driver) {
        super(driver);
    }

    public HomePage abrir() {
        driver.get(ConfigReader.get().app().baseUrl());
        esperarVisible(BUSCADOR);
        return this;
    }

    public HomePage seleccionarOperacion(String operacion) {
        click(Locator.of(
                By.xpath(String.format(BOTON_OPERACION, operacion)),
                "Botón de operación '" + operacion + "' (Comprar/Arrendar) del buscador principal"));
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
