package cl.guzman.automation.model;

/**
 * Cliente del portal generado para un escenario. Las credenciales son de prueba y se crean en cada ejecución.
 */
public record Cliente(String usuario, String nombre, String password) {

    public String primerNombre() {
        return nombre.split(" ")[0];
    }

    @Override
    public String toString() {
        return "Cliente[usuario=" + usuario + ", nombre=" + nombre + ", password=****]";
    }
}
