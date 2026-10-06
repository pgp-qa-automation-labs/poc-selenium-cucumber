package cl.guzman.automation.retry;

import cl.guzman.automation.config.ConfigReader;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reintenta un escenario fallido hasta retry.maxRetries veces. Si pasa en un reintento es inestable (flaky):
 * se informa, pero no se investiga ni se crea un issue. Si falla en todos los intentos, es un fallo consistente.
 */
public class ReintentoFallidos implements IRetryAnalyzer {

    // Cada escenario de Cucumber es una invocación distinta del mismo método: se cuenta por parámetros
    private static final Map<String, Integer> REINTENTOS = new ConcurrentHashMap<>();

    @Override
    public boolean retry(ITestResult resultado) {
        String escenario = resultado.getMethod().getQualifiedName() + Arrays.toString(resultado.getParameters());
        int hechos = REINTENTOS.merge(escenario, 1, Integer::sum);
        return hechos <= ConfigReader.get().retry().maxRetries();
    }
}
