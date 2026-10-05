package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@JsonClassDescription("Devuelve las peticiones de datos (fetch/XHR) que hizo el navegador durante el escenario, con su "
        + "resultado tal como lo recibió el navegador: código HTTP o el error de red. Es la única forma de ver fallas "
        + "intermitentes: una consulta posterior a la API puede responder bien aunque en el momento del fallo no lo hizo.")
public class LeerRed implements Supplier<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> TIPOS_DATOS = Set.of("XHR", "Fetch");
    private static final int MAX_PETICIONES = 40;

    @Override
    public String get() {
        TriageContext contexto = TriageContext.actual();
        try {
            List<String> peticiones = contexto.peticionesDeRed(() -> leer(contexto));
            long fallidas = peticiones.stream().filter(p -> p.contains("FALLÓ") || p.matches(".*-> [45]\\d\\d.*")).count();
            contexto.registrarUso("LeerRed", peticiones.size() + " peticiones de datos, " + fallidas + " con error");
            return peticiones.isEmpty()
                    ? "El navegador no registró peticiones de datos (fetch/XHR) en este escenario."
                    : String.join("\n", peticiones);
        } catch (RuntimeException e) {
            contexto.registrarUso("LeerRed", "error: " + e.getMessage());
            return "No se pudo leer la red del navegador: " + e.getMessage();
        }
    }

    /**
     * Arma una línea por petición a partir de los eventos de red de Chrome (DevTools Protocol).
     */
    private static List<String> leer(TriageContext contexto) {
        Map<String, String[]> peticiones = new LinkedHashMap<>(); // requestId -> [metodo, url, resultado]
        for (LogEntry entrada : contexto.driver.manage().logs().get(LogType.PERFORMANCE).getAll()) {
            JsonNode mensaje = parsear(entrada.getMessage()).path("message");
            JsonNode params = mensaje.path("params");
            String id = params.path("requestId").asText();
            switch (mensaje.path("method").asText()) {
                case "Network.requestWillBeSent" -> {
                    if (TIPOS_DATOS.contains(params.path("type").asText())) {
                        peticiones.put(id, new String[]{params.path("request").path("method").asText(),
                                params.path("request").path("url").asText(), "sin respuesta"});
                    }
                }
                case "Network.responseReceived" -> {
                    String[] peticion = peticiones.get(id);
                    if (peticion != null) {
                        peticion[2] = String.valueOf(params.path("response").path("status").asInt());
                    }
                }
                case "Network.loadingFailed" -> {
                    String[] peticion = peticiones.get(id);
                    if (peticion != null) {
                        peticion[2] = "FALLÓ (" + params.path("errorText").asText()
                                + (params.has("blockedReason") ? ", bloqueada: " + params.path("blockedReason").asText() : "") + ")";
                    }
                }
                default -> {
                }
            }
        }
        List<String> lineas = new ArrayList<>();
        peticiones.values().forEach(p -> lineas.add(p[0] + " " + p[1] + " -> " + p[2]));
        return lineas.subList(Math.max(0, lineas.size() - MAX_PETICIONES), lineas.size());
    }

    private static JsonNode parsear(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            return MAPPER.createObjectNode();
        }
    }
}
