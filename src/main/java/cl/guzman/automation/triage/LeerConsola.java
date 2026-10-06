package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.List;
import java.util.function.Supplier;

@JsonClassDescription("Devuelve los mensajes de la consola del navegador registrados hasta el fallo (errores de JavaScript, "
        + "recursos que no cargaron). Úsala para saber si el front tuvo errores propios.")
public class LeerConsola implements Supplier<String> {

    @Override
    public String get() {
        TriageContext contexto = TriageContext.actual();
        List<String> lineas = contexto.evidencia.consola().lines().toList();
        long errores = lineas.stream().filter(l -> l.startsWith("[SEVERE]")).count();
        contexto.registrarUso("LeerConsola", lineas.size() + " mensajes, " + errores + " errores");
        return lineas.isEmpty() ? "La consola del navegador no tenía mensajes." : String.join("\n", lineas);
    }
}
