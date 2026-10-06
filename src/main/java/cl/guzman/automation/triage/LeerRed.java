package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import java.util.List;
import java.util.function.Supplier;

@JsonClassDescription("Devuelve las peticiones de datos (fetch/XHR) que hizo el navegador durante el escenario, con su "
        + "resultado tal como lo recibió el navegador en ese momento: código HTTP o el error de red. Es la única forma de "
        + "ver fallas intermitentes: una consulta posterior a la API puede responder bien aunque en el momento del fallo no lo hizo.")
public class LeerRed implements Supplier<String> {

    @Override
    public String get() {
        TriageContext contexto = TriageContext.actual();
        List<String> peticiones = contexto.evidencia.red().lines().toList();
        long fallidas = peticiones.stream().filter(p -> p.contains("FALLÓ") || p.matches(".*-> [45]\\d\\d")).count();
        contexto.registrarUso("LeerRed", peticiones.size() + " peticiones de datos, " + fallidas + " con error");
        return peticiones.isEmpty()
                ? "El navegador no registró peticiones de datos (fetch/XHR) en este escenario."
                : String.join("\n", peticiones);
    }
}
