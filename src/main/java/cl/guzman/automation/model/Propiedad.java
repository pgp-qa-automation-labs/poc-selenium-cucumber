package cl.guzman.automation.model;

/**
 * Datos de una propiedad tal como se muestran en la UI, para comparar listado vs detalle.
 */
public record Propiedad(String id, String codigo, String nombre, String ubicacion, String precio) {
}
