package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.function.Supplier;

@JsonClassDescription("Devuelve el HTML visible de la página en el momento del fallo, simplificado (sin scripts ni estilos; "
        + "los elementos ocultos vienen marcados con data-oculto=\"true\"). Úsala para verificar si un elemento existía, "
        + "qué texto mostraba la página o si había mensajes de error en pantalla.")
public class LeerHtml implements Supplier<String> {

    @Override
    public String get() {
        TriageContext contexto = TriageContext.actual();
        String html = contexto.evidencia.html();
        contexto.registrarUso("LeerHtml", html.length() + " caracteres");
        return html.isBlank() ? "No se guardó el HTML de la página." : html;
    }
}
