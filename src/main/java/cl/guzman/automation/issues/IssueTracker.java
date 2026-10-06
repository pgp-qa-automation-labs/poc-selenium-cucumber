package cl.guzman.automation.issues;

import cl.guzman.automation.triage.Diagnostico;

import java.nio.file.Path;

/**
 * Gestor de proyectos donde se publican los diagnósticos del triage. Cada implementación decide el formato
 * (markdown en GitHub, ADF en Jira, HTML en Azure DevOps) a partir del mismo {@link Diagnostico}.
 */
public interface IssueTracker {

    String nombre();

    /**
     * Crea el issue, o agrega un comentario si ya existe uno abierto con la misma huella.
     * En modo dry-run no envía nada: devuelve el payload exacto que se habría enviado.
     *
     * @param captura captura de pantalla del fallo (puede ser null); cada gestor la adjunta o la enlaza
     */
    ResultadoIssue publicar(Diagnostico diagnostico, ContextoEjecucion contexto, Path captura, boolean dryRun);

    /**
     * Crea una tarea de mantenimiento por una reparación del self-healing, o comenta la existente.
     * Por defecto no aplica: en GitHub el PR de corrección ya registra el trabajo.
     */
    default ResultadoIssue publicarTarea(TareaReparacion tarea, ContextoEjecucion contexto, boolean dryRun) {
        return new ResultadoIssue(ResultadoIssue.Accion.OMITIDO, tarea.huella(), tarea.titulo(), tarea.prUrl(),
                "No aplica en " + nombre() + ": el PR de corrección ya registra el trabajo", null, null);
    }
}
