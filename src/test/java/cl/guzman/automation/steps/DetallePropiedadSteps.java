package cl.guzman.automation.steps;

import cl.guzman.automation.components.TarjetaPropiedad;
import cl.guzman.automation.context.TestContext;
import cl.guzman.automation.model.Propiedad;
import cl.guzman.automation.pages.DetallePropiedadPage;
import cl.guzman.automation.pages.ResultadosPage;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.assertj.core.api.SoftAssertions;

public class DetallePropiedadSteps {

    private final TestContext context;

    public DetallePropiedadSteps(TestContext context) {
        this.context = context;
    }

    @When("abre el detalle de la primera propiedad del listado")
    public void abreElDetalleDeLaPrimeraPropiedad() {
        ResultadosPage resultados = new ResultadosPage(context.getDriver());
        TarjetaPropiedad tarjeta = resultados.obtenerPrimeraTarjeta();
        context.setPropiedadSeleccionada(tarjeta.toPropiedad());
        resultados.abrirDetalle(tarjeta);
    }

    @Then("el detalle muestra el mismo título, ubicación, precio y código de la propiedad seleccionada")
    public void elDetalleMuestraLosMismosDatos() {
        Propiedad esperada = context.getPropiedadSeleccionada();
        Propiedad actual = new DetallePropiedadPage(context.getDriver()).obtenerPropiedad();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(actual.id()).as("Id en la URL").isEqualTo(esperada.id());
        // isNotBlank evita falsos positivos si un dato viniera vacío en ambas páginas
        soft.assertThat(actual.nombre()).as("Título").isNotBlank().isEqualTo(esperada.nombre());
        soft.assertThat(actual.ubicacion()).as("Ubicación").isNotBlank().isEqualTo(esperada.ubicacion());
        soft.assertThat(actual.precio()).as("Precio").isNotBlank().isEqualTo(esperada.precio());
        soft.assertThat(actual.codigo()).as("Código").isNotBlank().isEqualTo(esperada.codigo());
        soft.assertAll();
    }

    @Then("el detalle muestra las características {string} y {string}")
    public void elDetalleMuestraLasCaracteristicas(String primera, String segunda) {
        DetallePropiedadPage detalle = new DetallePropiedadPage(context.getDriver());

        SoftAssertions soft = new SoftAssertions();
        for (String caracteristica : new String[]{primera, segunda}) {
            soft.assertThat(detalle.obtenerCaracteristica(caracteristica).orElse(""))
                    .as("Valor de la característica '%s'", caracteristica)
                    .isNotBlank();
        }
        soft.assertAll();
    }
}
