package cl.guzman.automation.evidencia;

import cl.guzman.automation.triage.FalloEscenario;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Evidencia de un escenario fallido, guardada en disco en el momento del fallo. Es el "contrato" entre la etapa
 * de pruebas y la de triage: el agente la investiga después, sin necesidad del navegador.
 *
 * <pre>
 * target/evidencia/&lt;escenario&gt;/
 *   fallo.json    escenario, pasos, error, URL, intentos de self-healing
 *   captura.png   pantalla al fallar
 *   html.txt      HTML visible reducido
 *   consola.txt   consola del navegador
 *   red.txt       peticiones de datos del navegador y su resultado
 *   estado.txt    FALLIDO o INESTABLE (pasó en un reintento)
 * </pre>
 */
public record PaqueteEvidencia(Path directorio, FalloEscenario fallo, Estado estado) {

    public enum Estado {
        /** Falló en todos los intentos: es un fallo consistente y se investiga. */
        FALLIDO,
        /** Falló y luego pasó en un reintento: se informa como inestable, sin investigar. */
        INESTABLE
    }

    public Path captura() {
        return directorio.resolve("captura.png");
    }

    public String html() {
        return leer("html.txt");
    }

    public String consola() {
        return leer("consola.txt");
    }

    public String red() {
        return leer("red.txt");
    }

    private String leer(String archivo) {
        Path ruta = directorio.resolve(archivo);
        try {
            return Files.exists(ruta) ? Files.readString(ruta, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta, e);
        }
    }
}
