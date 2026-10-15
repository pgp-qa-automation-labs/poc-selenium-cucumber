package cl.guzman.automation.api;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.model.Cliente;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Acceso directo a la API de clientes del backend. Se usa para preparar datos (no para probar la UI):
 * registrar por API es más rápido y estable que pasar por el formulario de registro.
 */
public final class ClientesApi {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClientesApi() {
    }

    /**
     * Registra el cliente en el portal (POST /api/auth/register). Falla si el backend no lo acepta.
     */
    public static void registrar(Cliente cliente) {
        String url = ConfigReader.get().app().apiUrl() + "/api/auth/register";
        try {
            String cuerpo = MAPPER.writeValueAsString(Map.of(
                    "username", cliente.usuario(),
                    "nombre", cliente.nombre(),
                    "password", cliente.password(),
                    // El backend exige aceptar la Política de Privacidad (en la UI es un checkbox del registro)
                    "acepta_privacidad", true));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException("El registro de " + cliente.usuario() + " falló con HTTP "
                        + response.statusCode() + ": " + response.body());
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo registrar el cliente " + cliente.usuario() + " en " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Registro del cliente interrumpido", e);
        }
    }
}
