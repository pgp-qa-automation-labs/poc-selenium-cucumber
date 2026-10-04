package cl.guzman.automation.steps;

import cl.guzman.automation.context.TestContext;
import cl.guzman.automation.pages.HomePage;
import io.cucumber.java.es.Dado;

public class NavegacionSteps {

    private final TestContext context;

    public NavegacionSteps(TestContext context) {
        this.context = context;
    }

    @Dado("que el usuario está en el home de Guzmán Corretaje")
    public void queElUsuarioEstaEnElHome() {
        new HomePage(context.getDriver()).abrir();
    }
}
