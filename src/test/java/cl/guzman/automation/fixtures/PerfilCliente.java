package cl.guzman.automation.fixtures;

import java.util.Arrays;

/**
 * Perfiles que se pueden pedir desde la columna "perfil" de los Examples. Cada perfil define cómo se preparan
 * los datos del cliente. En un proyecto real, aquí iría el criterio de la consulta a la base de datos
 * (ej. "cliente con cuenta corriente activa").
 */
public enum PerfilCliente {

    COMPRADOR("que quiere comprar", true),
    CONSULTA("que consulta", true),
    ARRIENDO("que consulta arriendos", true),
    /** No acepta la Política de Privacidad: la API rechaza el registro y el escenario se omite. */
    SIN_CONSENTIMIENTO("sin consentimiento", false);

    private final String texto;
    private final boolean aceptaPrivacidad;

    PerfilCliente(String texto, boolean aceptaPrivacidad) {
        this.texto = texto;
        this.aceptaPrivacidad = aceptaPrivacidad;
    }

    public boolean aceptaPrivacidad() {
        return aceptaPrivacidad;
    }

    public static PerfilCliente desdeTexto(String texto) {
        return Arrays.stream(values())
                .filter(perfil -> perfil.texto.equalsIgnoreCase(texto.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Perfil de cliente desconocido en los Examples: '" + texto
                        + "'. Perfiles válidos: " + Arrays.stream(values()).map(p -> "'" + p.texto + "'").toList()));
    }
}
