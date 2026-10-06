package cl.guzman.automation.issues;

import cl.guzman.automation.evidencia.AgrupadorFallos;
import cl.guzman.automation.healing.Reparacion;

/**
 * Tarea de mantenimiento para QA: el self-healing reparó un locator por un cambio real del sitio.
 * No es un bug (la aplicación funciona), sino código de pruebas desactualizado con una corrección propuesta en un PR.
 * Se publica en Jira y Azure DevOps; en GitHub el PR ya cumple ese rol.
 *
 * @param huella identificador estable de la tarea (página + locator original): evita tareas duplicadas
 * @param prUrl  enlace al PR que corrige el locator (puede ser null si no se conoce)
 */
public record TareaReparacion(
        String huella,
        String pagina,
        String descripcion,
        String locatorOriginal,
        String locatorNuevo,
        int confianza,
        String razon,
        String ambiente,
        String prUrl) {

    public static TareaReparacion desde(Reparacion reparacion, String ambiente, String prUrl) {
        return new TareaReparacion(
                AgrupadorFallos.huella("reparacion|" + reparacion.pagina() + "|" + reparacion.locatorOriginal(), ambiente),
                reparacion.pagina(),
                reparacion.descripcion(),
                reparacion.locatorOriginal(),
                reparacion.locatorNuevo(),
                reparacion.confianza(),
                reparacion.razon(),
                ambiente,
                prUrl);
    }

    public String titulo() {
        String titulo = "[Self-healing] Actualizar locator en " + pagina + ": " + descripcion;
        return titulo.length() > 255 ? titulo.substring(0, 252) + "..." : titulo;
    }

    static final String AVISO = "🔧 Tarea creada automáticamente por el self-healing de la suite E2E. La aplicación funciona: "
            + "cambió un elemento del front y el locator de las pruebas quedó desactualizado. La IA encontró el elemento y "
            + "las pruebas siguieron pasando; falta revisar y aprobar la corrección propuesta.";

    static final String RECOMENDACION = "Para evitar que vuelva a ocurrir, se sugiere al equipo de front agregar un atributo "
            + "estable (por ejemplo data-testid) a este elemento, de modo que las pruebas no dependan de clases CSS.";
}
