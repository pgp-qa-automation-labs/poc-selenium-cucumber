package cl.guzman.automation.healing;

import org.openqa.selenium.By;

/**
 * Locator con significado de negocio. La descripción es lo que permite a la IA reconocer el elemento
 * cuando el selector deja de funcionar, por eso debe decir QUÉ es y PARA QUÉ sirve, no cómo se ve.
 *
 * @param by          selector original
 * @param descripcion función del elemento, ej. "Botón BUSCAR del buscador principal del home"
 * @param multiple    true si el selector apunta intencionalmente a varios elementos (ej. tarjetas de un listado)
 */
public record Locator(By by, String descripcion, boolean multiple) {

    public static Locator of(By by, String descripcion) {
        return new Locator(by, descripcion, false);
    }

    public static Locator ofMany(By by, String descripcion) {
        return new Locator(by, descripcion, true);
    }

    /**
     * Clave estable para cachear reparaciones durante la ejecución.
     */
    public String clave() {
        return by.toString();
    }

    @Override
    public String toString() {
        return descripcion + " [" + by + "]";
    }
}
