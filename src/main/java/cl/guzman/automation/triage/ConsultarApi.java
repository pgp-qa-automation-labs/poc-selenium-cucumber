package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;

@JsonClassDescription("Hace una petición GET a la API del backend del ambiente bajo prueba y devuelve el código HTTP, "
        + "el tiempo de respuesta y el inicio del cuerpo. Úsala para comprobar si el backend está disponible o si devuelve "
        + "los datos que el front necesita. Solo permite rutas que empiecen con /api/.")
public class ConsultarApi implements Supplier<String> {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_CUERPO = 1500;

    @JsonPropertyDescription("Ruta relativa de la API, por ejemplo /api/hora o /api/properties")
    public String ruta;

    @Override
    public String get() {
        String resultado = consultar();
        TriageContext.actual().registrarUso("ConsultarApi", resultado.lines().findFirst().orElse(""));
        return resultado;
    }

    private String consultar() {
        if (ruta == null || !ruta.startsWith("/api/") || ruta.contains("..") || ruta.contains("://")) {
            return "Ruta no permitida: debe empezar con /api/ y ser relativa.";
        }
        String apiUrl = TriageContext.actual().config.app().apiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return "El ambiente no tiene apiUrl configurada.";
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl + ruta)).timeout(TIMEOUT).GET().build();
        long inicio = System.currentTimeMillis();
        try {
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            String cuerpo = response.body() == null ? "" : response.body();
            return "GET " + ruta + " -> HTTP " + response.statusCode() + " en " + (System.currentTimeMillis() - inicio) + " ms\n"
                    + (cuerpo.length() > MAX_CUERPO ? cuerpo.substring(0, MAX_CUERPO) + "... [" + cuerpo.length() + " caracteres]" : cuerpo);
        } catch (IOException e) {
            return "GET " + ruta + " falló tras " + (System.currentTimeMillis() - inicio) + " ms: "
                    + e.getClass().getSimpleName() + " " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Consulta interrumpida";
        }
    }
}
