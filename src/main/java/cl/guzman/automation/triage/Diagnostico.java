package cl.guzman.automation.triage;

import java.time.Instant;
import java.util.List;

/**
 * Resultado del agente de triage para un grupo de fallos con la misma causa. Es independiente del gestor de proyectos:
 * es el insumo para crear un issue en GitHub, Jira o Azure DevOps.
 *
 * @param huella              identificador estable del problema (no depende de la IA): evita issues duplicados
 * @param escenariosAfectados todos los escenarios del grupo (el primero es el que se investigó)
 * @param capturaArchivo      nombre del archivo de la captura en la carpeta de capturas del triage
 * @param capturaUrl          URL pública de la captura, si se publicó (para mostrarla en el issue y en el resumen)
 */
public record Diagnostico(
        Instant fecha,
        String huella,
        List<String> escenariosAfectados,
        String capturaArchivo,
        String capturaUrl,
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

    /**
     * Copia del diagnóstico con la captura asociada (se conoce después del análisis, al publicar la evidencia).
     */
    public Diagnostico conCaptura(String archivo, String url) {
        return new Diagnostico(fecha, huella, escenariosAfectados, archivo, url, escenario, feature, pasoFallido, this.url,
                ambiente, simulado, categoria, severidad, areaResponsable, titulo, causaProbable, evidencias,
                accionRecomendada, confianza, modelo, iteraciones, tokensEntrada, tokensSalida);
    }

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
