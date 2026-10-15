package cl.guzman.automation.utils;

import cl.guzman.automation.model.Cliente;
import net.datafaker.Faker;

import java.util.Locale;

/**
 * Genera datos de prueba realistas en español de Chile con Datafaker.
 */
public final class GeneradorDatos {

    /** Prefijo de los usuarios creados por la automatización, para identificarlos y limpiarlos en la base. */
    public static final String PREFIJO_USUARIO = "qa_auto_";

    private static final Faker FAKER = new Faker(new Locale("es", "CL"));

    private GeneradorDatos() {
    }

    /**
     * Cliente con nombre real, usuario único y contraseña válida para el registro del portal
     * (usuario: solo letras, números, puntos y guiones bajos; contraseña: mínimo 6 caracteres).
     */
    public static Cliente clienteAleatorio() {
        String nombre = FAKER.name().firstName();
        String apellido = FAKER.name().lastName();
        String usuario = PREFIJO_USUARIO
                + TextUtils.normalizar(nombre).replaceAll("[^a-z0-9]", "")
                + "_" + FAKER.number().digits(6);
        String password = FAKER.credentials().password(10, 14, true, false, true);
        return new Cliente(usuario, nombre + " " + apellido, password);
    }
}
