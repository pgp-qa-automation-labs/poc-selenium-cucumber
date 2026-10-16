package cl.guzman.automation.runners;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.config.EnvironmentConfig;
import cl.guzman.automation.utils.WarmUpUtils;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de contrato de la API que usan los fixtures (karate/contrato). En CI corren en su propia etapa,
 * antes de la UI: si fallan, los escenarios de Selenium no se ejecutan.
 */
public class ContratoApiTest {

    @Test
    public void contratoDeLaApi() {
        EnvironmentConfig config = ConfigReader.get();
        EnvironmentConfig.WarmUp warmUp = config.warmUp();
        if (warmUp.enabled()) {
            WarmUpUtils.esperarDisponibilidad(
                    config.app().apiUrl() + warmUp.healthPath(),
                    Duration.ofSeconds(warmUp.maxSeconds()),
                    Duration.ofSeconds(warmUp.pollSeconds()));
        }

        Results resultados = Runner.path("classpath:karate/contrato")
                .systemProperty("api.url", config.app().apiUrl())
                .reportDir("target/karate-reports")
                .outputCucumberJson(true)
                .parallel(1);

        assertThat(resultados.getFailCount())
                .as("Escenarios de contrato fallidos:%n%s", resultados.getErrorMessages())
                .isZero();
    }
}
