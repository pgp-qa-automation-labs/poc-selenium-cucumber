package cl.guzman.automation.runners;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;

@CucumberOptions(
        features = "classpath:features",
        glue = {"cl.guzman.automation.steps", "cl.guzman.automation.hooks"},
        plugin = {
                "pretty",
                "html:target/cucumber-reports/cucumber.html",
                "json:target/cucumber-reports/cucumber.json",
                "cl.guzman.automation.listeners.PasosListener"
        })
public class TestRunner extends AbstractTestNGCucumberTests {
}
