package cl.guzman.automation.triage;

import java.util.List;

/**
 * Datos del escenario fallido que el framework entrega al agente como punto de partida
 * (se guardan en fallo.json dentro del paquete de evidencia).
 *
 * @param pasos               pasos ejecutados en orden, con su estado (PASSED, FAILED, SKIPPED...)
 * @param error               mensaje del error del paso fallido (resumido)
 * @param reparacionesHealing resumen en markdown de los intentos de self-healing del escenario (puede estar vacío)
 * @param simulado            true si el escenario simula una falla (demo); no se informa al agente para no sesgarlo
 * @param url                 URL del navegador al fallar
 * @param tituloPagina        título de la página al fallar
 * @param intentos            cantidad de ejecuciones del escenario (1 + reintentos)
 */
public record FalloEscenario(
        String escenario,
        String feature,
        List<String> tags,
        List<Paso> pasos,
        String pasoFallido,
        String error,
        String ambiente,
        String reparacionesHealing,
        boolean simulado,
        String url,
        String tituloPagina,
        int intentos) {

    public record Paso(String texto, String estado) {
    }
}
