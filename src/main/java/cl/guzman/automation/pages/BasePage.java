package cl.guzman.automation.pages;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.healing.HealingEngine;
import cl.guzman.automation.healing.Locator;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Base de todas las páginas. Toda interacción con elementos pasa por aquí, con esperas explícitas
 * y self-healing: si un locator deja de encontrar su elemento, se intenta reparar con IA y se reintenta la acción.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    protected final WebDriverWait wait;
    private final HealingEngine healing;

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(ConfigReader.get().timeouts().explicitWaitSeconds()));
        this.healing = HealingEngine.get();
    }

    protected WebElement esperarVisible(Locator locator) {
        return conHealing(locator, by -> wait.until(ExpectedConditions.visibilityOfElementLocated(by)));
    }

    protected List<WebElement> esperarVisibles(Locator locator) {
        return conHealing(locator, by -> wait.until(ExpectedConditions.visibilityOfAllElementsLocatedBy(by)));
    }

    protected void click(Locator locator) {
        conHealing(locator, by -> {
            WebElement elemento = wait.until(ExpectedConditions.elementToBeClickable(by));
            scrollAlCentro(elemento);
            elemento.click();
            return elemento;
        });
    }

    protected String obtenerTexto(Locator locator) {
        return esperarVisible(locator).getText().trim();
    }

    /**
     * Espera a que el select esté habilitado y tenga la opción (las opciones se cargan dinámicamente) y la selecciona.
     */
    protected void seleccionarPorTexto(Locator locator, String texto) {
        conHealing(locator, by -> {
            wait.until(ExpectedConditions.elementToBeClickable(by));
            wait.until(d -> new Select(d.findElement(by)).getOptions().stream()
                    .anyMatch(opcion -> opcion.getText().trim().equals(texto)));
            new Select(driver.findElement(by)).selectByVisibleText(texto);
            return null;
        });
    }

    protected void esperarUrlContiene(String fragmento) {
        wait.until(ExpectedConditions.urlContains(fragmento));
    }

    protected void scrollAlCentro(WebElement elemento) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", elemento);
    }

    /**
     * Ejecuta la acción con el selector vigente del locator. Si el elemento no aparece, pide al motor de healing
     * un selector reparado y reintenta una vez. Si no hay reparación posible, propaga el error original.
     *
     * <p>Si el selector todavía encuentra el elemento, el fallo no es del locator (ej. un select sin la opción
     * esperada porque el backend no respondió) y el healing no interviene: el error se propaga tal cual.
     */
    protected <T> T conHealing(Locator locator, Function<By, T> accion) {
        By vigente = healing.efectivo(locator);
        try {
            return accion.apply(vigente);
        } catch (TimeoutException | NoSuchElementException original) {
            if (encuentraElementoVisible(vigente)) {
                throw original;
            }
            By reparado = healing.reparar(driver, locator, getClass().getSimpleName())
                    .orElseThrow(() -> original);
            return accion.apply(reparado);
        }
    }

    private boolean encuentraElementoVisible(By by) {
        try {
            return driver.findElements(by).stream().anyMatch(WebElement::isDisplayed);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
