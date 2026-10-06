package cl.guzman.automation.triage;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.evidencia.RecolectorEvidencia;

import java.nio.file.Path;
import java.util.List;

/**
 * Punto de entrada de la etapa "Evaluación de triage" del pipeline: investiga la evidencia que dejó la etapa de
 * pruebas, sin volver a ejecutarlas. También permite reprocesar evidencia guardada.
 *
 * <pre>mvn exec:java -Dexec.mainClass=cl.guzman.automation.triage.EvaluarTriage -Dexec.args=ruta/a/evidencia</pre>
 *
 * La variable de entorno EVIDENCIAS_URL_BASE indica dónde quedarán publicadas las capturas (para mostrarlas en los issues).
 */
public final class EvaluarTriage {

    private EvaluarTriage() {
    }

    public static void main(String[] args) {
        Path evidencia = args.length > 0 ? Path.of(args[0]) : RecolectorEvidencia.RAIZ;
        List<Diagnostico> diagnosticos = EvaluadorTriage.evaluar(ConfigReader.get(), evidencia, System.getenv("EVIDENCIAS_URL_BASE"));
        System.out.println("Triage: " + diagnosticos.size() + " diagnóstico(s) generado(s) a partir de " + evidencia);
    }
}
