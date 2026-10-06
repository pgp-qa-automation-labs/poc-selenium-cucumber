package cl.guzman.automation.evidencia;

import cl.guzman.automation.healing.DomSnapshot;
import cl.guzman.automation.triage.FalloEscenario;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Guarda y lee los paquetes de evidencia de los escenarios fallidos (ver {@link PaqueteEvidencia}).
 */
public final class RecolectorEvidencia {

    public static final Path RAIZ = Path.of("target", "evidencia");
    private static final int MAX_HTML = 150_000;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private RecolectorEvidencia() {
    }

    /**
     * Guarda la evidencia del fallo con el navegador aún abierto. Si el escenario ya tenía evidencia de un intento
     * anterior, se reemplaza: siempre queda la del último intento.
     *
     * @param idEscenario identificador estable del escenario (igual en todos sus reintentos)
     */
    public static Path guardar(WebDriver driver, String idEscenario, FalloEscenario fallo, byte[] capturaPng) {
        Path directorio = RAIZ.resolve(carpeta(fallo.escenario(), idEscenario));
        try {
            Files.createDirectories(directorio);
            Files.writeString(directorio.resolve("fallo.json"), MAPPER.writeValueAsString(fallo), StandardCharsets.UTF_8);
            if (capturaPng != null) {
                Files.write(directorio.resolve("captura.png"), capturaPng);
            }
            DomSnapshot.Resultado dom = DomSnapshot.capturar(driver, MAX_HTML);
            Files.writeString(directorio.resolve("html.txt"),
                    (dom.truncado() ? "[HTML truncado: " + dom.largoOriginal() + " caracteres originales]\n" : "") + dom.html(),
                    StandardCharsets.UTF_8);
            Files.write(directorio.resolve("consola.txt"), NavegadorLogs.consola(driver), StandardCharsets.UTF_8);
            Files.write(directorio.resolve("red.txt"), NavegadorLogs.red(driver), StandardCharsets.UTF_8);
            Files.writeString(directorio.resolve("estado.txt"), PaqueteEvidencia.Estado.FALLIDO.name(), StandardCharsets.UTF_8);
            return directorio;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la evidencia en " + directorio, e);
        }
    }

    /**
     * Un escenario que falló pasó en un reintento: su evidencia queda como inestable (no se investiga).
     */
    public static void marcarInestable(String nombreEscenario, String idEscenario) {
        Path directorio = RAIZ.resolve(carpeta(nombreEscenario, idEscenario));
        if (Files.isDirectory(directorio)) {
            try {
                Files.writeString(directorio.resolve("estado.txt"), PaqueteEvidencia.Estado.INESTABLE.name(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("No se pudo marcar como inestable " + directorio, e);
            }
        }
    }

    public static List<PaqueteEvidencia> listar(Path raiz) {
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        List<PaqueteEvidencia> paquetes = new ArrayList<>();
        try (Stream<Path> carpetas = Files.list(raiz)) {
            for (Path carpeta : carpetas.filter(Files::isDirectory).sorted().toList()) {
                Path fallo = carpeta.resolve("fallo.json");
                if (!Files.exists(fallo)) {
                    continue;
                }
                Path estado = carpeta.resolve("estado.txt");
                paquetes.add(new PaqueteEvidencia(carpeta,
                        MAPPER.readValue(fallo.toFile(), FalloEscenario.class),
                        Files.exists(estado)
                                ? PaqueteEvidencia.Estado.valueOf(Files.readString(estado, StandardCharsets.UTF_8).trim())
                                : PaqueteEvidencia.Estado.FALLIDO));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la evidencia de " + raiz, e);
        }
        return paquetes;
    }

    private static String carpeta(String nombreEscenario, String idEscenario) {
        String slug = nombreEscenario.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String sufijo = Integer.toHexString(idEscenario.hashCode());
        return (slug.length() > 50 ? slug.substring(0, 50) : slug) + "-" + sufijo;
    }
}
