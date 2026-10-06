package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@JsonClassDescription("Compara el HTML visible de la página al fallar con el de la última ejecución exitosa en el mismo "
        + "punto del flujo (justo antes del paso que falló). Devuelve solo las diferencias: líneas que desaparecieron (-) "
        + "y que aparecieron (+). Úsala para saber exactamente qué cambió en la página respecto de cuando todo funcionaba.")
public class CompararConEjecucionExitosa implements Supplier<String> {

    private static final int CONTEXTO = 1;
    private static final int MAX_LINEAS = 200;

    @Override
    public String get() {
        TriageContext contexto = TriageContext.actual();
        String base = contexto.evidencia.htmlLineaBase();
        if (base.isBlank()) {
            contexto.registrarUso("CompararConEjecucionExitosa", "sin línea base");
            return "No hay una ejecución exitosa previa con los mismos pasos: no se puede comparar.";
        }
        List<String> antes = lineas(base);
        List<String> ahora = lineas(contexto.evidencia.html());
        Patch<String> diferencias = DiffUtils.diff(antes, ahora);
        if (diferencias.getDeltas().isEmpty()) {
            contexto.registrarUso("CompararConEjecucionExitosa", "sin diferencias");
            return "El HTML visible es igual al de la última ejecución exitosa.\n" + contexto.evidencia.infoLineaBase();
        }
        List<String> diff = UnifiedDiffUtils.generateUnifiedDiff("ultima_ejecucion_exitosa", "al_fallar", antes, diferencias, CONTEXTO);
        contexto.registrarUso("CompararConEjecucionExitosa", diferencias.getDeltas().size() + " diferencias");
        String recortado = diff.size() > MAX_LINEAS
                ? String.join("\n", diff.subList(0, MAX_LINEAS)) + "\n... [" + (diff.size() - MAX_LINEAS) + " líneas más]"
                : String.join("\n", diff);
        return "Línea base (última ejecución exitosa):\n" + contexto.evidencia.infoLineaBase()
                + "\n\nDiferencias (" + diferencias.getDeltas().size() + " bloques):\n" + recortado
                + "\n\nOjo: parte de las diferencias puede ser contenido dinámico que carga a destiempo (precios, "
                + "conversiones, tooltips, horas). Eso no indica un despliegue; céntrate en lo relacionado con el paso fallido.";
    }

    /**
     * El HTML reducido viene en una sola línea: se separa por etiqueta para que el diff sea legible.
     */
    private static List<String> lineas(String html) {
        return Arrays.asList(html.replace("><", ">\n<").split("\n"));
    }
}
