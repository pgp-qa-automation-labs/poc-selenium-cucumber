package cl.guzman.automation.utils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza textos de la UI para compararlos de forma estable.
 */
public final class TextUtils {

    private TextUtils() {
    }

    /**
     * Quita íconos/emojis (ej. "📍") y colapsa espacios. Mantiene mayúsculas y tildes.
     */
    public static String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto
                .replaceAll("[\\p{So}\\p{Cs}\\uFE0F]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Convierte un número en formato chileno ("41.098,15", "$ 238.287.074") a BigDecimal.
     * Los puntos son separadores de miles y la coma es el separador decimal.
     */
    public static BigDecimal numeroChileno(String texto) {
        String numero = texto == null ? "" : texto.replaceAll("[^0-9.,]", "").replace(".", "").replace(',', '.');
        if (numero.isEmpty()) {
            throw new IllegalArgumentException("No hay un número en el texto: '" + texto + "'");
        }
        return new BigDecimal(numero);
    }

    /**
     * Limpia, quita tildes y pasa a minúsculas: "ÑUÑOA" y "Ñuñoa" quedan iguales ("nunoa").
     */
    public static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(limpiar(texto), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }
}
