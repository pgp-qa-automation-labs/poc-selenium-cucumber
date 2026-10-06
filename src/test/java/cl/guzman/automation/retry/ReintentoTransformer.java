package cl.guzman.automation.retry;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Aplica {@link ReintentoFallidos} a los escenarios de Cucumber (registrado como listener en testng.xml).
 */
public class ReintentoTransformer implements IAnnotationTransformer {

    @Override
    @SuppressWarnings("rawtypes")
    public void transform(ITestAnnotation anotacion, Class clase, Constructor constructor, Method metodo) {
        anotacion.setRetryAnalyzer(ReintentoFallidos.class);
    }
}
