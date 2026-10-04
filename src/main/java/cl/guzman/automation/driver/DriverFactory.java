package cl.guzman.automation.driver;

import cl.guzman.automation.config.EnvironmentConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.time.Duration;
import java.util.Locale;

/**
 * Crea instancias de WebDriver según la configuración. Selenium Manager resuelve el driver del navegador.
 */
public final class DriverFactory {

    private DriverFactory() {
    }

    public static WebDriver create(EnvironmentConfig config) {
        EnvironmentConfig.Browser browser = config.browser();

        WebDriver driver = switch (browser.name().toLowerCase(Locale.ROOT)) {
            case "chrome" -> new ChromeDriver(configure(new ChromeOptions(), browser));
            case "edge" -> new EdgeDriver(configure(new EdgeOptions(), browser));
            default -> throw new IllegalArgumentException("Navegador no soportado: " + browser.name());
        };

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.timeouts().pageLoadSeconds()));
        if (!browser.headless()) {
            driver.manage().window().maximize();
        }
        return driver;
    }

    private static <T extends ChromiumOptions<T>> T configure(T options, EnvironmentConfig.Browser browser) {
        options.addArguments("--disable-notifications", "--lang=es-CL");
        if (browser.headless()) {
            // Sin pantalla no se puede maximizar: se fija un tamaño para que el layout sea estable en CI
            options.addArguments(
                    "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                    "--window-size=" + browser.windowWidth() + "," + browser.windowHeight());
        }
        return options;
    }
}
