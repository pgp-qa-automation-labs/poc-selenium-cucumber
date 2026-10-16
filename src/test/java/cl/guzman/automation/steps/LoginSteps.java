package cl.guzman.automation.steps;

import cl.guzman.automation.context.TestContext;
import cl.guzman.automation.fixtures.ClienteFixture;
import cl.guzman.automation.model.Cliente;
import cl.guzman.automation.pages.LoginPage;
import cl.guzman.automation.pages.PortalClientePage;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static org.assertj.core.api.Assertions.assertThat;

public class LoginSteps {

    private final TestContext context;
    private final ClienteFixture clienteFixture;
    private Scenario scenario;
    private PortalClientePage portal;

    public LoginSteps(TestContext context, ClienteFixture clienteFixture) {
        this.context = context;
        this.clienteFixture = clienteFixture;
    }

    @Before("@login")
    public void guardarEscenario(Scenario scenario) {
        this.scenario = scenario;
    }

    @Given("que existe un cliente {string} que cumple el criterio para iniciar sesión")
    public void queExisteUnClienteQueCumpleElCriterio(String perfil) {
        String motivo = clienteFixture.prepararCliente(perfil);
        scenario.log("Cliente de prueba: " + context.getCliente().usuario() + " · " + motivo);
    }

    @When("inicia sesión con su usuario y contraseña")
    public void iniciaSesionConSuUsuarioYContrasena() {
        Cliente cliente = context.getCliente();
        portal = new LoginPage(context.getDriver())
                .abrir()
                .iniciarSesion(cliente.usuario(), cliente.password());
    }

    @Then("ve el portal de clientes con su nombre")
    public void veElPortalDeClientesConSuNombre() {
        String saludo = portal.esperarCarga().obtenerSaludo();
        assertThat(saludo)
                .as("Saludo del portal de clientes")
                .contains(context.getCliente().primerNombre());
    }
}
