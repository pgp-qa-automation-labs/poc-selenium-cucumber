package cl.guzman.automation.steps;

import cl.guzman.automation.components.TarjetaPropiedad;
import cl.guzman.automation.context.TestContext;
import cl.guzman.automation.model.Propiedad;
import cl.guzman.automation.pages.DetallePropiedadPage;
import cl.guzman.automation.pages.ResultadosPage;
import cl.guzman.automation.utils.TextUtils;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

public class DetallePropiedadSteps {

    // Rango razonable para la UF (CLP): detecta valores vacíos, en cero o mal formateados sin atarse al valor del día
    private static final BigDecimal UF_MINIMA = new BigDecimal("30000");
    private static final BigDecimal UF_MAXIMA = new BigDecimal("60000");
    // La página redondea el monto en pesos; se acepta una diferencia de hasta 0,5 %
    private static final BigDecimal TOLERANCIA_CONVERSION = new BigDecimal("0.005");

    private final TestContext context;

    public DetallePropiedadSteps(TestContext context) {
        this.context = context;
    }

    @Then("el detalle muestra la conversión a pesos según el valor de la UF del día")
    public void elDetalleMuestraLaConversionAPesos() {
        DetallePropiedadPage detalle = new DetallePropiedadPage(context.getDriver());
        String precio = detalle.obtenerPrecio();
        assertThat(precio)
                .as("El escenario requiere una propiedad con precio en UF (precio mostrado: '%s')", precio)
                .startsWith("UF");

        BigDecimal precioUf = TextUtils.numeroChileno(precio);
        DetallePropiedadPage.ConversionUf conversion = detalle.obtenerConversionUf();
        BigDecimal esperadoClp = precioUf.multiply(conversion.valorUf());
        BigDecimal diferencia = conversion.montoClp().subtract(esperadoClp).abs()
                .divide(esperadoClp, 6, RoundingMode.HALF_UP);

        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(conversion.valorUf())
                .as("Valor de la UF del día mostrado ('%s')", conversion.textoUf())
                .isBetween(UF_MINIMA, UF_MAXIMA);
        soft.assertThat(diferencia)
                .as("Conversión a pesos: %s × UF %s debería ≈ %s, la página muestra '%s'",
                        precioUf, conversion.valorUf(), esperadoClp.setScale(0, RoundingMode.HALF_UP), conversion.textoClp())
                .isLessThanOrEqualTo(TOLERANCIA_CONVERSION);
        soft.assertAll();
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
