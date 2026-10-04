package cl.guzman.automation.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Despierta servicios que se duermen por inactividad (ej. Render free) antes de abrir el navegador,
 * para que las esperas de la UI no tengan que absorber el arranque en frío.
 */
public final class WarmUpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(WarmUpUtils.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private WarmUpUtils() {
    }

    public static void esperarDisponibilidad(String url, Duration maximo, Duration intervalo) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).GET().build();
        Instant inicio = Instant.now();
        Instant limite = inicio.plus(maximo);
        String ultimoError = "sin respuesta";

        LOG.info("Warm-up: esperando que {} responda (máximo {} s)", url, maximo.toSeconds());
        while (Instant.now().isBefore(limite)) {
            try {
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    LOG.info("Warm-up: servicio disponible en {} s", Duration.between(inicio, Instant.now()).toSeconds());
                    return;
                }
                ultimoError = "HTTP " + response.statusCode();
            } catch (IOException e) {
                ultimoError = e.getClass().getSimpleName() + ": " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Warm-up interrumpido", e);
            }
            LOG.info("Warm-up: aún no disponible ({}), reintentando...", ultimoError);
            dormir(intervalo);
        }
        throw new IllegalStateException(
                "El servicio " + url + " no respondió en " + maximo.toSeconds() + " s. Último error: " + ultimoError);
    }

    private static void dormir(Duration duracion) {
        try {
            Thread.sleep(duracion.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Warm-up interrumpido", e);
        }
    }
}
