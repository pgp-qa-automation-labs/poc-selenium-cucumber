package cl.guzman.automation.evidencia;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Agrupa los fallos que comparten la misma causa visible, sin usar IA, para investigar uno por grupo.
 *
 * <p>Regla (estricta a propósito, para no mezclar problemas distintos):
 * <ul>
 *   <li>Si el navegador tuvo llamadas a la API con error, la clave son esas llamadas (método, ruta y resultado):
 *       escenarios que fallan en pasos distintos por la misma API caída quedan juntos.</li>
 *   <li>Si no, la clave es el paso fallido más el tipo y el mensaje del error, sin números ni identificadores.</li>
 * </ul>
 */
public final class AgrupadorFallos {

    private static final Pattern ERROR_HTTP = Pattern.compile(".* -> [45]\\d\\d$");

    private AgrupadorFallos() {
    }

    /**
     * @return grupos ordenados de mayor a menor cantidad de escenarios; la clave es determinística
     */
    public static Map<String, List<PaqueteEvidencia>> agrupar(List<PaqueteEvidencia> fallos) {
        Map<String, List<PaqueteEvidencia>> grupos = new LinkedHashMap<>();
        fallos.forEach(p -> grupos.computeIfAbsent(clave(p), k -> new ArrayList<>()).add(p));
        Map<String, List<PaqueteEvidencia>> ordenados = new LinkedHashMap<>();
        grupos.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<PaqueteEvidencia>> e) -> e.getValue().size()).reversed())
                .forEach(e -> ordenados.put(e.getKey(), e.getValue()));
        return ordenados;
    }

    /**
     * Identificador corto y estable de un grupo: no depende de la redacción ni de la clasificación de la IA,
     * así el mismo problema siempre produce la misma huella (y el mismo issue).
     */
    public static String huella(String clave, String ambiente) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((clave + "|" + ambiente).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    static String clave(PaqueteEvidencia paquete) {
        TreeSet<String> llamadasConError = new TreeSet<>();
        for (String linea : paquete.red().lines().toList()) {
            boolean fallida = linea.contains("-> FALLÓ") || ERROR_HTTP.matcher(linea).matches();
            if (fallida && !linea.startsWith("[NO DISPONIBLE]")) {
                llamadasConError.add(normalizarLlamada(linea));
            }
        }
        if (!llamadasConError.isEmpty()) {
            return "red|" + String.join(",", llamadasConError);
        }
        return "error|" + paquete.fallo().pasoFallido() + "|" + normalizarError(paquete.fallo().error());
    }

    /**
     * "GET https://api/x/api/properties/48?y=1 -> FALLÓ (...)" queda como "GET /api/properties/{n} -> FALLÓ".
     */
    private static String normalizarLlamada(String linea) {
        String[] partes = linea.split(" ", 3);
        String metodo = partes[0];
        String resto = partes.length > 2 ? partes[1] + " " + partes[2] : linea;
        String url = resto.substring(0, resto.indexOf(" -> ") < 0 ? resto.length() : resto.indexOf(" -> "));
        String resultado = resto.contains(" -> ") ? resto.substring(resto.indexOf(" -> ") + 4) : "";
        String ruta;
        try {
            ruta = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            ruta = url;
        }
        ruta = ruta.replaceAll("/\\d+", "/{n}");
        return metodo + " " + ruta + " -> " + (resultado.startsWith("FALLÓ") ? "FALLÓ" : resultado);
    }

    private static String normalizarError(String error) {
        if (error == null) {
            return "";
        }
        String tipo = error.contains(":") ? error.substring(0, error.indexOf(':')) : error;
        String mensaje = error.contains(":") ? error.substring(error.indexOf(':') + 1) : "";
        mensaje = mensaje.lines().findFirst().orElse("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[0-9a-f]{8,}", "#")
                .replaceAll("\\d+", "#")
                .replaceAll("\\s+", " ")
                .trim();
        return tipo + ":" + (mensaje.length() > 120 ? mensaje.substring(0, 120) : mensaje);
    }
}
