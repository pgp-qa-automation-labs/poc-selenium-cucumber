package cl.guzman.automation.evidencia;

import cl.guzman.automation.healing.DomSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Línea base: cómo se veía la página después de cada paso en la última ejecución exitosa.
 *
 * <p>Se identifica por la secuencia de pasos ejecutados (no por el escenario): un escenario que falla en el paso N
 * se compara con el estado de cualquier escenario exitoso que haya ejecutado los mismos N-1 pasos previos.
 * Así el agente de triage puede ver exactamente qué cambió respecto de la última vez que todo funcionó.
 *
 * <pre>
 * .lineas-base/&lt;huella de los pasos&gt;.html.txt   HTML visible reducido
 * .lineas-base/&lt;huella de los pasos&gt;.png        captura
 * .lineas-base/&lt;huella de los pasos&gt;.json       fecha, URL y pasos
 * </pre>
 */
public final class LineaBase {

    public static final Path RAIZ = Path.of(".lineas-base");
    private static final int MAX_HTML = 150_000;
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    // Estados del escenario en curso: se guardan solo si el escenario termina aprobado
    private static final ThreadLocal<List<Estado>> PENDIENTES = ThreadLocal.withInitial(ArrayList::new);

    private LineaBase() {
    }

    private record Estado(String clave, List<String> pasos, String url, String html, byte[] captura) {
    }

    /**
     * Registra el estado de la página después de un paso aprobado (se confirma al terminar el escenario).
     *
     * @param pasos textos de los pasos ejecutados hasta ahora, incluido el actual
     */
    public static void registrarPaso(WebDriver driver, List<String> pasos) {
        try {
            String html = DomSnapshot.capturar(driver, MAX_HTML).html();
            byte[] captura = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            PENDIENTES.get().add(new Estado(clave(pasos), List.copyOf(pasos), driver.getCurrentUrl(), html, captura));
        } catch (RuntimeException e) {
            // La línea base es una ayuda para el triage: nunca debe hacer fallar una prueba
        }
    }

    /**
     * El escenario terminó aprobado (y no es una simulación): sus estados pasan a ser la nueva línea base.
     */
    public static void confirmar() {
        try {
            Files.createDirectories(RAIZ);
            for (Estado estado : PENDIENTES.get()) {
                Files.writeString(RAIZ.resolve(estado.clave() + ".html.txt"), estado.html(), StandardCharsets.UTF_8);
                Files.write(RAIZ.resolve(estado.clave() + ".png"), estado.captura());
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("fecha", Instant.now().toString());
                info.put("url", estado.url());
                info.put("pasos", estado.pasos());
                Files.writeString(RAIZ.resolve(estado.clave() + ".json"), MAPPER.writeValueAsString(info), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la línea base en " + RAIZ, e);
        } finally {
            descartar();
        }
    }

    public static void descartar() {
        PENDIENTES.get().clear();
    }

    /**
     * Copia a la evidencia del fallo la línea base del estado previo al paso fallido, si existe.
     *
     * @param pasosPrevios textos de los pasos que pasaron antes del paso fallido
     * @return true si había línea base para esa secuencia de pasos
     */
    public static boolean copiarA(Path evidencia, List<String> pasosPrevios) {
        if (pasosPrevios.isEmpty()) {
            return false;
        }
        String clave = clave(pasosPrevios);
        Path html = RAIZ.resolve(clave + ".html.txt");
        if (!Files.exists(html)) {
            return false;
        }
        try {
            Files.copy(html, evidencia.resolve("base-html.txt"), StandardCopyOption.REPLACE_EXISTING);
            copiarSiExiste(RAIZ.resolve(clave + ".png"), evidencia.resolve("base-captura.png"));
            copiarSiExiste(RAIZ.resolve(clave + ".json"), evidencia.resolve("base.json"));
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo copiar la línea base a " + evidencia, e);
        }
    }

    private static void copiarSiExiste(Path origen, Path destino) throws IOException {
        if (Files.exists(origen)) {
            Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String clave(List<String> pasos) {
        return AgrupadorFallos.huella(String.join("\n", pasos), "linea-base");
    }
}
