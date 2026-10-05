package cl.guzman.automation.issues;

/**
 * @param url         enlace al issue creado o comentado (null en dry-run, omitido o error)
 * @param payload     cuerpo exacto enviado (o que se habría enviado) al gestor, para revisión
 * @param vistaPrevia cómo se verá el issue, legible (markdown), para revisar los datos antes de habilitar el envío
 */
public record ResultadoIssue(Accion accion, String huella, String titulo, String url, String detalle,
                             String payload, String vistaPrevia) {

    public enum Accion {
        CREADO,
        COMENTADO,
        SIMULADO,
        OMITIDO,
        ERROR
    }
}
