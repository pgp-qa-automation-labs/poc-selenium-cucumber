package cl.guzman.automation.hooks;

import cl.guzman.automation.api.ClientesApi;
import cl.guzman.automation.context.TestContext;
import cl.guzman.automation.model.Cliente;
import cl.guzman.automation.utils.GeneradorDatos;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixture de los escenarios @login: antes de cada uno genera un cliente con datos aleatorios, lo registra
 * por API y lo deja en el contexto. Así cada escenario parte con un cliente nuevo y no depende de usuarios fijos.
 */
public class ClienteFixture {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteFixture.class);

    private final TestContext context;

    public ClienteFixture(TestContext context) {
        this.context = context;
    }

    @Before("@login")
    public void prepararCliente(Scenario scenario) {
        Cliente cliente = GeneradorDatos.clienteAleatorio();
        ClientesApi.registrar(cliente);
        context.setCliente(cliente);
        LOG.info("Cliente de prueba registrado: {}", cliente);
        scenario.log("Cliente de prueba: " + cliente.usuario() + " (" + cliente.nombre() + ")");
    }
}
