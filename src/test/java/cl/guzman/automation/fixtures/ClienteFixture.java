package cl.guzman.automation.fixtures;

import cl.guzman.automation.config.ConfigReader;
import cl.guzman.automation.context.TestContext;
import cl.guzman.automation.model.Cliente;
import cl.guzman.automation.utils.GeneradorDatos;
import com.intuit.karate.Runner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;

import java.util.Map;

/**
 * Fixture de clientes: prepara los datos de un escenario a partir del perfil pedido en los Examples.
 *
 * <pre>
 * 1. Obtener datos        GeneradorDatos (Datafaker)      → en el banco: consulta a la base de datos
 * 2. Validar por API      Karate (registrar_cliente)      → registra y evalúa el criterio
 * 3. ¿Cumple el criterio? sí → deja el cliente en el contexto y el escenario continúa
 *                         no → SkipException: Cucumber marca el escenario como omitido, con el motivo
 * </pre>
 *
 * PicoContainer lo inyecta en los steps junto con el TestContext del escenario.
 */
public class ClienteFixture {

    private static final Logger LOG = LoggerFactory.getLogger(ClienteFixture.class);
    private static final String FEATURE_REGISTRO = "classpath:karate/clientes/registrar_cliente.feature";

    private final TestContext context;

    public ClienteFixture(TestContext context) {
        this.context = context;
    }

    /**
     * Prepara un cliente del perfil indicado. Si no cumple el criterio, el escenario se omite.
     *
     * @return el motivo de la validación, para dejarlo en el reporte
     */
    public String prepararCliente(String perfilEnExamples) {
        PerfilCliente perfil = PerfilCliente.desdeTexto(perfilEnExamples);
        Cliente cliente = GeneradorDatos.clienteAleatorio();

        Map<String, Object> resultado = Runner.runFeature(FEATURE_REGISTRO, Map.of(
                "apiUrl", ConfigReader.get().app().apiUrl(),
                "usuario", cliente.usuario(),
                "nombre", cliente.nombre(),
                "password", cliente.password(),
                "aceptaPrivacidad", perfil.aceptaPrivacidad()), false);

        // Un error técnico (Karate no llegó a evaluar el criterio) no es "no cumple": debe fallar, no omitirse
        if (!resultado.containsKey("elegible")) {
            throw new IllegalStateException("Karate no evaluó el criterio en " + FEATURE_REGISTRO
                    + " (¿el feature falló o fue omitido?). Variables devueltas: " + resultado.keySet());
        }
        String motivo = String.valueOf(resultado.get("motivo"));
        if (!Boolean.TRUE.equals(resultado.get("elegible"))) {
            LOG.warn("Cliente {} ({}) no cumple el criterio: {}", cliente.usuario(), perfil, motivo);
            throw new SkipException("Escenario omitido para el perfil '" + perfilEnExamples + "'. " + motivo);
        }
        context.setCliente(cliente);
        LOG.info("Cliente {} ({}) listo: {}", cliente, perfil, motivo);
        return motivo;
    }
}
