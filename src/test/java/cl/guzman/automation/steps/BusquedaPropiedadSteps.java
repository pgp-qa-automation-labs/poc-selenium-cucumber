package cl.guzman.automation.steps;

import cl.guzman.automation.components.TarjetaPropiedad;
import cl.guzman.automation.context.TestContext;
import cl.guzman.automation.pages.HomePage;
import cl.guzman.automation.pages.ResultadosPage;
import cl.guzman.automation.utils.TextUtils;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.assertj.core.api.SoftAssertions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BusquedaPropiedadSteps {

    private final TestContext context;

    public BusquedaPropiedadSteps(TestContext context) {
        this.context = context;
    }

    @When("selecciona la operación {string}")
    public void seleccionaLaOperacion(String operacion) {
        new HomePage(context.getDriver()).seleccionarOperacion(operacion);
    }

    @When("filtra por tipo {string}, región {string} y comuna {string}")
    public void filtraPorTipoRegionYComuna(String tipo, String region, String comuna) {
        new HomePage(context.getDriver())
                .seleccionarTipo(tipo)
                .seleccionarRegion(region)
                .seleccionarComuna(comuna);
    }

    @When("presiona Buscar")
    public void presionaBuscar() {
        new HomePage(context.getDriver()).buscar();
    }

    @Then("ve el listado {string} con al menos {int} resultado(s)")
    public void veElListadoConAlMenosResultados(String titulo, int minimo) {
        ResultadosPage resultados = new ResultadosPage(context.getDriver());
        List<TarjetaPropiedad> tarjetas = resultados.obtenerTarjetas();

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(resultados.obtenerTitulo()).as("Título del listado").isEqualTo(titulo);
        soft.assertThat(tarjetas).as("Propiedades mostradas en el listado").hasSizeGreaterThanOrEqualTo(minimo);
        soft.assertThat(resultados.obtenerConteo()).as("Conteo informado vs tarjetas mostradas").isEqualTo(tarjetas.size());
        soft.assertAll();
    }

    @Then("todas las propiedades del listado están en la comuna {string}")
    public void todasLasPropiedadesEstanEnLaComuna(String comuna) {
        List<TarjetaPropiedad> tarjetas = new ResultadosPage(context.getDriver()).obtenerTarjetas();
        String comunaNormalizada = TextUtils.normalizar(comuna);

        assertThat(tarjetas).as("Propiedades del listado").isNotEmpty();
        SoftAssertions soft = new SoftAssertions();
        tarjetas.forEach(tarjeta -> soft.assertThat(TextUtils.normalizar(tarjeta.ubicacion()))
                .as("Ubicación de %s (%s)", tarjeta.codigo(), tarjeta.ubicacion())
                .contains(comunaNormalizada));
        soft.assertAll();
    }
}
