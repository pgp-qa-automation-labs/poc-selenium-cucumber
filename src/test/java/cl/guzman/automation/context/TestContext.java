package cl.guzman.automation.context;

import cl.guzman.automation.driver.DriverManager;
import cl.guzman.automation.model.Cliente;
import cl.guzman.automation.model.Propiedad;
import org.openqa.selenium.WebDriver;

/**
 * Estado compartido entre clases de steps durante un escenario. PicoContainer crea una instancia por escenario.
 */
public class TestContext {

    private Propiedad propiedadSeleccionada;
    private Cliente cliente;

    public WebDriver getDriver() {
        return DriverManager.getDriver();
    }

    public Propiedad getPropiedadSeleccionada() {
        if (propiedadSeleccionada == null) {
            throw new IllegalStateException("No se ha seleccionado ninguna propiedad en este escenario");
        }
        return propiedadSeleccionada;
    }

    public void setPropiedadSeleccionada(Propiedad propiedadSeleccionada) {
        this.propiedadSeleccionada = propiedadSeleccionada;
    }

    public Cliente getCliente() {
        if (cliente == null) {
            throw new IllegalStateException("No hay un cliente preparado: el escenario debe tener el tag @login");
        }
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }
}
