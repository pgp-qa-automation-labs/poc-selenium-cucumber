package cl.guzman.automation.hooks;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.driver.DriverFactory;
import cl.guzman.automation.driver.DriverManager;
import cl.guzman.automation.utils.ScreenshotUtils;
import cl.guzman.automation.utils.WarmUpUtils;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class Hooks {

    private static final Logger LOG = LoggerFactory.getLogger(Hooks.class);

    @BeforeAll
    public static void prepararAmbiente() {
        EnvironmentConfig config = ConfigReader.get();
        LOG.info("Ambiente: {} | baseUrl: {} | navegador: {} (headless={})",
                config.env(), config.app().baseUrl(), config.browser().name(), config.browser().headless());

        EnvironmentConfig.WarmUp warmUp = config.warmUp();
        if (warmUp.enabled()) {
            WarmUpUtils.esperarDisponibilidad(
                    config.app().apiUrl() + warmUp.healthPath(),
                    Duration.ofSeconds(warmUp.maxSeconds()),
                    Duration.ofSeconds(warmUp.pollSeconds()));
        }
    }

    @Before
    public void iniciarNavegador() {
        DriverManager.setDriver(DriverFactory.create(ConfigReader.get()));
    }

    @After
    public void cerrarNavegador(Scenario scenario) {
        try {
            if (scenario.isFailed() && DriverManager.hasDriver()) {
                scenario.attach(ScreenshotUtils.capturar(DriverManager.getDriver()), "image/png", scenario.getName());
            }
        } finally {
            DriverManager.quitDriver();
        }
    }
}
