package cl.guzman.automation.healing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Corrige en el código fuente de las Pages los locators que el self-healing reparó durante la ejecución.
 * Reemplaza el literal exacto del selector original (ej. {@code By.cssSelector(".buscador-hero__btn")})
 * por el reparado. Si el literal no aparece exactamente una vez (por ejemplo, un selector armado con
 * String.format), no toca el archivo y lo informa para revisión manual.
 */
public final class LocatorPatcher {

    private static final Pattern BY_TO_STRING = Pattern.compile("^By\\.(cssSelector|xpath|id|name|className|tagName): (.*)$", Pattern.DOTALL);

    private LocatorPatcher() {
    }

    /**
     * Aplica las reparaciones aceptadas y no simuladas sobre los archivos bajo {@code raizFuentes}.
     */
    public static List<Resultado> aplicar(List<Reparacion> reparaciones, Path raizFuentes) {
        List<Resultado> resultados = new ArrayList<>();
        for (Reparacion reparacion : reparaciones) {
            if (!reparacion.aplicada() || reparacion.simulada()) {
                continue;
            }
            resultados.add(aplicar(reparacion, raizFuentes));
        }
        return resultados;
    }

    private static Resultado aplicar(Reparacion reparacion, Path raizFuentes) {
        Optional<String> original = literal(reparacion.locatorOriginal());
        Optional<String> nuevo = literal(reparacion.locatorNuevo());
        if (original.isEmpty() || nuevo.isEmpty()) {
            return Resultado.omitido(reparacion, null, "Tipo de selector no soportado para corrección automática");
        }

        Optional<Path> archivo = buscarArchivo(raizFuentes, reparacion.pagina() + ".java");
        if (archivo.isEmpty()) {
            return Resultado.omitido(reparacion, null, "No se encontró " + reparacion.pagina() + ".java en " + raizFuentes);
        }

        try {
            String contenido = Files.readString(archivo.get(), StandardCharsets.UTF_8);
            int apariciones = contar(contenido, original.get());
            if (apariciones != 1) {
                return Resultado.omitido(reparacion, archivo.get(), apariciones == 0
                        ? "El selector no está escrito literalmente en el código (probablemente se arma dinámicamente)"
                        : "El selector aparece " + apariciones + " veces; se requiere revisión manual");
            }
            Files.writeString(archivo.get(), contenido.replace(original.get(), nuevo.get()), StandardCharsets.UTF_8);
            return new Resultado(reparacion, archivo.get(), true, original.get() + " → " + nuevo.get());
        } catch (IOException e) {
            return Resultado.omitido(reparacion, archivo.get(), "Error al leer/escribir el archivo: " + e.getMessage());
        }
    }

    /**
     * Convierte el toString de un By ("By.cssSelector: .foo") en el código Java que lo crea ({@code By.cssSelector(".foo")}).
     */
    static Optional<String> literal(String byToString) {
        if (byToString == null) {
            return Optional.empty();
        }
        Matcher matcher = BY_TO_STRING.matcher(byToString);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of("By." + matcher.group(1) + "(\"" + escaparJava(matcher.group(2)) + "\")");
    }

    private static String escaparJava(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static int contar(String texto, String buscado) {
        int cantidad = 0;
        for (int i = texto.indexOf(buscado); i >= 0; i = texto.indexOf(buscado, i + buscado.length())) {
            cantidad++;
        }
        return cantidad;
    }

    private static Optional<Path> buscarArchivo(Path raiz, String nombre) {
        if (!Files.isDirectory(raiz)) {
            return Optional.empty();
        }
        try (Stream<Path> archivos = Files.walk(raiz)) {
            return archivos.filter(p -> p.getFileName().toString().equals(nombre)).findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * @param corregido true si se modificó el archivo
     * @param detalle   cambio aplicado o motivo por el que se omitió
     */
    public record Resultado(Reparacion reparacion, Path archivo, boolean corregido, String detalle) {

        static Resultado omitido(Reparacion reparacion, Path archivo, String motivo) {
            return new Resultado(reparacion, archivo, false, motivo);
        }
    }
}
