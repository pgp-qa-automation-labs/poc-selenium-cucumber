package cl.guzman.automation.pages;

import cl.guzman.automation.config.ConfigReader;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;

/**
 * Base de todas las páginas. Toda interacción con elementos pasa por aquí, con esperas explícitas.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigReader.get().timeouts().explicitWaitSeconds()));
    }

    protected WebElement esperarVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected List<WebElement> esperarVisibles(By locator) {
        return wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(locator));
    }

    protected void click(By locator) {
        WebElement elemento = wait.until(ExpectedConditions.elementToBeClickable(locator));
        scrollAlCentro(elemento);
        elemento.click();
    }

    protected String obtenerTexto(By locator) {
        return esperarVisible(locator).getText().trim();
    }

    /**
     * Espera a que el select esté habilitado y tenga la opción (las opciones se cargan dinámicamente) y la selecciona.
     */
    protected void seleccionarPorTexto(By locator, String texto) {
        wait.until(ExpectedConditions.elementToBeClickable(locator));
        wait.until(d -> new Select(d.findElement(locator)).getOptions().stream()
                .anyMatch(opcion -> opcion.getText().trim().equals(texto)));
        new Select(driver.findElement(locator)).selectByVisibleText(texto);
    }

    protected void esperarUrlContiene(String fragmento) {
        wait.until(ExpectedConditions.urlContains(fragmento));
    }

    protected void scrollAlCentro(WebElement elemento) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", elemento);
    }
}
