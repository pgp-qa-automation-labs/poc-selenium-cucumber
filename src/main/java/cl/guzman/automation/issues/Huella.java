package cl.guzman.automation.issues;

import cl.guzman.automation.evidencia.AgrupadorFallos;
import cl.guzman.automation.triage.Diagnostico;

/**
 * Identificador estable de un problema, para no abrir issues duplicados en cada ejecución.
 * Lo calcula el triage a partir de la evidencia del grupo (no de la clasificación de la IA, que puede variar).
 */
public final class Huella {

    private Huella() {
    }

    public static String de(Diagnostico d) {
        if (d.huella() != null && !d.huella().isBlank()) {
            return d.huella();
        }
        // Diagnósticos antiguos sin huella: se usa la ubicación del fallo, que tampoco depende de la IA
        return AgrupadorFallos.huella(d.escenario() + "|" + d.pasoFallido(), d.ambiente());
    }
}
