package cl.guzman.automation.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Cliente HTTP mínimo para las APIs REST de los gestores (JSON in/out, autenticación Basic).
 */
final class ClienteHttp {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final String autorizacion;

    private ClienteHttp(String autorizacion) {
        this.autorizacion = autorizacion;
    }

    /**
     * Autenticación Basic: Jira usa correo:token y Azure DevOps usa ":" + PAT.
     */
    static ClienteHttp basic(String usuario, String clave) {
        String credenciales = (usuario == null ? "" : usuario) + ":" + clave;
        return new ClienteHttp("Basic " + Base64.getEncoder().encodeToString(credenciales.getBytes(StandardCharsets.UTF_8)));
    }

    JsonNode enviar(String metodo, String url, String cuerpo, String contentType) throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", autorizacion)
                .header("Accept", "application/json")
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cuerpo != null) {
            peticion.header("Content-Type", contentType);
        }
        HttpResponse<String> respuesta = http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() >= 300) {
            String detalle = respuesta.body() == null ? "" : respuesta.body();
            throw new IOException("HTTP " + respuesta.statusCode() + " en " + metodo + " " + url + ": "
                    + (detalle.length() > 300 ? detalle.substring(0, 300) + "..." : detalle));
        }
        return respuesta.body() == null || respuesta.body().isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(respuesta.body());
    }

    /**
     * Envía un cuerpo binario (ej. un archivo adjunto) con cabeceras adicionales.
     */
    JsonNode enviarBytes(String url, byte[] cuerpo, String contentType, String... cabeceras)
            throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", autorizacion)
                .header("Accept", "application/json")
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo));
        for (int i = 0; i + 1 < cabeceras.length; i += 2) {
            peticion.header(cabeceras[i], cabeceras[i + 1]);
        }
        HttpResponse<String> respuesta = http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() >= 300) {
            String detalle = respuesta.body() == null ? "" : respuesta.body();
            throw new IOException("HTTP " + respuesta.statusCode() + " en POST " + url + ": "
                    + (detalle.length() > 300 ? detalle.substring(0, 300) + "..." : detalle));
        }
        return respuesta.body() == null || respuesta.body().isBlank() ? MAPPER.createObjectNode() : MAPPER.readTree(respuesta.body());
    }

    /**
     * Cuerpo multipart/form-data con un único archivo (como lo pide la API de adjuntos de Jira).
     */
    static byte[] multipart(String boundary, String campo, String nombreArchivo, String tipo, byte[] contenido) {
        String cabecera = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + campo + "\"; filename=\"" + nombreArchivo + "\"\r\n"
                + "Content-Type: " + tipo + "\r\n\r\n";
        String cierre = "\r\n--" + boundary + "--\r\n";
        byte[] inicio = cabecera.getBytes(StandardCharsets.UTF_8);
        byte[] fin = cierre.getBytes(StandardCharsets.UTF_8);
        byte[] cuerpo = new byte[inicio.length + contenido.length + fin.length];
        System.arraycopy(inicio, 0, cuerpo, 0, inicio.length);
        System.arraycopy(contenido, 0, cuerpo, inicio.length, contenido.length);
        System.arraycopy(fin, 0, cuerpo, inicio.length + contenido.length, fin.length);
        return cuerpo;
    }

    static String json(Object valor) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(valor);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo serializar el payload", e);
        }
    }

    static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
