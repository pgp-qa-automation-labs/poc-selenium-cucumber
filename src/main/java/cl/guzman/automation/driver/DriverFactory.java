package cl.guzman.automation.driver;

import cl.guzman.automation.config.EnvironmentConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import org.openqa.selenium.logging.LogType;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * Crea instancias de WebDriver según la configuración. Selenium Manager resuelve el driver del navegador.
 */
public final class DriverFactory {

    private DriverFactory() {
    }

    public static WebDriver create(EnvironmentConfig config) {
        EnvironmentConfig.Browser browser = config.browser();

        WebDriver driver = switch (browser.name().toLowerCase(Locale.ROOT)) {
            case "chrome" -> new ChromeDriver(configure(new ChromeOptions(), browser, "goog:loggingPrefs"));
            case "edge" -> new EdgeDriver(configure(new EdgeOptions(), browser, "ms:loggingPrefs"));
            default -> throw new IllegalArgumentException("Navegador no soportado: " + browser.name());
        };

        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(config.timeouts().pageLoadSeconds()));
        if (!browser.headless()) {
            driver.manage().window().maximize();
        }
        return driver;
    }

    private static <T extends ChromiumOptions<T>> T configure(T options, EnvironmentConfig.Browser browser, String loggingPrefs) {
        options.addArguments("--disable-notifications", "--lang=es-CL");
        // Guarda la consola y las peticiones de red del navegador para que el triage pueda revisarlas cuando un escenario falla
        options.setCapability(loggingPrefs, Map.of(LogType.BROWSER, "ALL", LogType.PERFORMANCE, "ALL"));
        options.setExperimentalOption("perfLoggingPrefs", Map.of("enableNetwork", true, "enablePage", false));
        if (browser.headless()) {
            // Sin pantalla no se puede maximizar: se fija un tamaño para que el layout sea estable en CI
            options.addArguments(
                    "--headless=new", "--no-sandbox", "--disable-dev-shm-usage",
                    "--window-size=" + browser.windowWidth() + "," + browser.windowHeight());
        }
        return options;
    }
}
