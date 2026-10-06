package cl.guzman.automation.evidencia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lee la consola y las peticiones de red que registró el navegador durante el escenario.
 * Los logs de Selenium se vacían al leerlos: se leen una vez, al guardar la evidencia.
 */
final class NavegadorLogs {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> TIPOS_DATOS = Set.of("XHR", "Fetch");
    private static final int MAX_LINEAS = 50;

    private NavegadorLogs() {
    }

    /**
     * Mensajes de la consola: "[NIVEL] mensaje".
     */
    static List<String> consola(WebDriver driver) {
        try {
            List<LogEntry> entradas = driver.manage().logs().get(LogType.BROWSER).getAll();
            return entradas.stream()
                    .skip(Math.max(0, entradas.size() - MAX_LINEAS))
                    .map(e -> "[" + e.getLevel() + "] " + recortar(e.getMessage()))
                    .toList();
        } catch (RuntimeException e) {
            return List.of("[NO DISPONIBLE] No se pudo leer la consola del navegador: " + e.getMessage());
        }
    }

    /**
     * Peticiones de datos (fetch/XHR) con su resultado: "GET url -> 200" o "GET url -> FALLÓ (motivo)".
     */
    static List<String> red(WebDriver driver) {
        try {
            Map<String, String[]> peticiones = new LinkedHashMap<>(); // requestId -> [metodo, url, resultado]
            for (LogEntry entrada : driver.manage().logs().get(LogType.PERFORMANCE).getAll()) {
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
            return lineas.subList(Math.max(0, lineas.size() - MAX_LINEAS), lineas.size());
        } catch (RuntimeException e) {
            return List.of("[NO DISPONIBLE] No se pudo leer la red del navegador: " + e.getMessage());
        }
    }

    private static JsonNode parsear(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            return MAPPER.createObjectNode();
        }
    }

    private static String recortar(String texto) {
        return texto.length() > 500 ? texto.substring(0, 500) + "..." : texto;
    }
}
