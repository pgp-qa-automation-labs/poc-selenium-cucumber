package cl.guzman.automation.triage;

import java.time.Instant;
import java.util.List;

/**
 * Resultado del agente de triage para un escenario fallido. Es independiente del gestor de proyectos:
 * es el insumo para crear un issue en GitHub, Jira o Azure DevOps.
 */
public record Diagnostico(
        Instant fecha,
        String escenario,
        String feature,
        String pasoFallido,
        String url,
        String ambiente,
        boolean simulado,
        Categoria categoria,
        Severidad severidad,
        Area areaResponsable,
        String titulo,
        String causaProbable,
        List<String> evidencias,
        String accionRecomendada,
        int confianza,
        String modelo,
        int iteraciones,
        long tokensEntrada,
        long tokensSalida) {

    public enum Categoria {
        /** La aplicación no se comporta como debe: hay un defecto en el front o en el backend. */
        BUG_APLICACION,
        /** El ambiente o un servicio del que depende no estaba disponible (API caída, timeout, error 5xx). */
        AMBIENTE_NO_DISPONIBLE,
        /** El front cambió de forma que afecta el flujo (elemento eliminado, oculto o con otro propósito). */
        CAMBIO_FUNCIONAL_UI,
        /** Los datos esperados por la prueba ya no existen o cambiaron (ej. no hay propiedades para el filtro). */
        DATOS_DE_PRUEBA,
        /** El problema está en la automatización: locator, espera, aserción o lógica del test. */
        PROBLEMA_DEL_TEST,
        /** No hay evidencia suficiente para concluir. */
        INDETERMINADO
    }

    public enum Severidad {
        ALTA,
        MEDIA,
        BAJA
    }

    public enum Area {
        FRONTEND,
        BACKEND,
        INFRAESTRUCTURA,
        QA
    }
}
