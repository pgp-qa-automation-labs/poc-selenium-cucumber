package cl.guzman.automation.issues;

import cl.guzman.automation.triage.Diagnostico;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identificador estable de un fallo: mismo escenario, mismo paso y misma categoría producen la misma huella,
 * aunque cambie la redacción del diagnóstico. Sirve para no abrir issues duplicados en cada ejecución.
 */
public final class Huella {

    private Huella() {
    }

    public static String de(Diagnostico d) {
        String base = d.escenario() + "|" + d.pasoFallido() + "|" + d.categoria() + "|" + d.ambiente();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
