package cl.guzman.automation.triage;

import java.util.List;

/**
 * Datos del escenario fallido que el framework entrega al agente como punto de partida.
 *
 * @param pasos             pasos ejecutados en orden, con su estado (PASSED, FAILED, SKIPPED...)
 * @param error             mensaje del error del paso fallido (resumido)
 * @param reparacionesHealing resumen en markdown de los intentos de self-healing del escenario (puede estar vacío)
 */
public record FalloEscenario(
        String escenario,
        String feature,
        List<String> tags,
        List<Paso> pasos,
        String pasoFallido,
        String error,
        String ambiente,
        String reparacionesHealing) {

    public record Paso(String texto, String estado) {
    }
}
