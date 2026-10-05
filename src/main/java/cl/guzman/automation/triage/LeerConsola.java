package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.util.List;
import java.util.function.Supplier;

@JsonClassDescription("Devuelve los últimos mensajes de la consola del navegador (errores de JavaScript, recursos que no "
        + "cargaron, respuestas HTTP con error de la API). Úsala para saber si el front tuvo errores o no pudo comunicarse con el backend.")
public class LeerConsola implements Supplier<String> {

    private static final int MAX_ENTRADAS = 50;

    @Override
    public String get() {
        try {
            List<LogEntry> entradas = TriageContext.actual().driver.manage().logs().get(LogType.BROWSER).getAll();
            if (entradas.isEmpty()) {
                return "La consola del navegador no tiene mensajes.";
            }
            StringBuilder salida = new StringBuilder();
            entradas.stream()
                    .skip(Math.max(0, entradas.size() - MAX_ENTRADAS))
                    .forEach(e -> salida.append('[').append(e.getLevel()).append("] ").append(recortar(e.getMessage())).append('\n'));
            return salida.toString();
        } catch (RuntimeException e) {
            return "No se pudo leer la consola del navegador: " + e.getMessage();
        }
    }

    private static String recortar(String texto) {
        return texto.length() > 500 ? texto.substring(0, 500) + "..." : texto;
    }
}
