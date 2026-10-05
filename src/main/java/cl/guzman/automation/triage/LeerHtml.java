package cl.guzman.automation.triage;

import cl.guzman.automation.healing.DomSnapshot;
import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.function.Supplier;

@JsonClassDescription("Devuelve el HTML visible actual de la página en el navegador, simplificado (sin scripts ni estilos; "
        + "los elementos ocultos vienen marcados con data-oculto=\"true\"). Úsala para verificar si un elemento existe, "
        + "qué texto muestra la página o si hay mensajes de error en pantalla.")
public class LeerHtml implements Supplier<String> {

    @Override
    public String get() {
        TriageContext contexto = TriageContext.actual();
        DomSnapshot.Resultado dom = DomSnapshot.capturar(contexto.driver, contexto.config.healing().maxDomChars());
        contexto.registrarUso("LeerHtml", dom.largoOriginal() + " caracteres" + (dom.truncado() ? " (truncado)" : ""));
        return (dom.truncado() ? "[HTML truncado: " + dom.largoOriginal() + " caracteres originales]\n" : "") + dom.html();
    }
}
