package cl.guzman.automation.issues;

import cl.guzman.automation.triage.Diagnostico;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Contenido común de un issue, igual para todos los gestores. Cada {@link IssueTracker} solo decide el formato
 * (markdown, ADF, HTML), así los tres reciben exactamente los mismos datos.
 */
final class ContenidoIssue {

    static final String ETIQUETA_BASE = "triage-ia";
    static final String AVISO_IA = "🤖 Issue creado automáticamente por el agente de triage de la suite E2E. "
            + "Es un diagnóstico asistido por IA: valida la evidencia antes de actuar.";
    static final String AVISO_DEMO = "⚠️ Demo: el fallo proviene de un escenario que simula un problema; no es un incidente real.";

    private ContenidoIssue() {
    }

    static String titulo(Diagnostico d) {
        return (d.simulado() ? "[Demo] " : "") + "[" + d.severidad() + "] " + d.titulo();
    }

    /**
     * @param separador separador entre tipo y valor ("severidad:alta" en GitHub; "severidad-alta" donde ':' no conviene)
     */
    static List<String> etiquetas(Diagnostico d, String separador) {
        List<String> etiquetas = new ArrayList<>(List.of(
                ETIQUETA_BASE,
                "severidad" + separador + d.severidad().name().toLowerCase(Locale.ROOT),
                "categoria" + separador + d.categoria().name().toLowerCase(Locale.ROOT).replace('_', '-'),
                "area" + separador + d.areaResponsable().name().toLowerCase(Locale.ROOT)));
        if (d.simulado()) {
            etiquetas.add("demo");
        }
        return etiquetas;
    }

    /**
     * Etiqueta con la huella del fallo, para encontrar el issue abierto en gestores que no buscan en la descripción.
     */
    static String etiquetaHuella(String huella) {
        return "triage-huella-" + huella;
    }

    static String resumen(Diagnostico d) {
        return "Severidad: " + d.severidad() + " · Categoría: " + d.categoria() + " · Área: " + d.areaResponsable()
                + " · Confianza: " + d.confianza() + "%";
    }

    /**
     * Filas de contexto (escenario, paso, URL, ejecución...). El enlace a la ejecución va aparte en {@link ContextoEjecucion}.
     */
    static Map<String, String> contexto(Diagnostico d, ContextoEjecucion c) {
        Map<String, String> filas = new LinkedHashMap<>();
        filas.put("Escenario", d.escenario());
        if (d.escenariosAfectados() != null && d.escenariosAfectados().size() > 1) {
            filas.put("Escenarios afectados", d.escenariosAfectados().size() + " con la misma evidencia: "
                    + String.join("; ", d.escenariosAfectados()));
        }
        filas.put("Feature", d.feature());
        filas.put("Paso fallido", d.pasoFallido());
        filas.put("URL", d.url());
        filas.put("Ambiente", d.ambiente());
        filas.put("Origen", c.origen());
        filas.put("Rama / commit", c.rama() + " / " + c.commit());
        filas.put("Análisis", d.modelo() + ", " + d.iteraciones() + " iteraciones");
        return filas;
    }

    static final String CAPTURA_EN_ARTEFACTOS = "La captura de pantalla está en los artefactos de la ejecución.";

    static String comentarioRepeticion(Diagnostico d, ContextoEjecucion c) {
        return "🔁 El fallo se repitió (" + c.origen() + ", rama " + c.rama() + ", commit " + c.commit() + "). "
                + "Confianza del nuevo diagnóstico: " + d.confianza() + "%.";
    }
}
