package cl.guzman.automation.healing;

import java.time.Instant;

/**
 * Registro de un intento de self-healing: qué elemento falló, qué propuso la IA y si se aplicó.
 * Contiene lo necesario para, más adelante, corregir el locator en el código fuente.
 */
public record Reparacion(
        Instant fecha,
        String pagina,
        String descripcion,
        String locatorOriginal,
        String locatorNuevo,
        SugerenciaLocator.TipoCambio tipoCambio,
        int confianza,
        String razon,
        boolean aplicada,
        String motivoRechazo,
        String modelo,
        long tokensEntrada,
        long tokensSalida) {
}
