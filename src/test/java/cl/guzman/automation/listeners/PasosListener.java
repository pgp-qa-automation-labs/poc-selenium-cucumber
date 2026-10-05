package cl.guzman.automation.listeners;

import cl.guzman.automation.triage.FalloEscenario;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepFinished;

import java.util.ArrayList;
import java.util.List;

/**
 * Registra los pasos ejecutados y el error del paso fallido de cada escenario. La API de hooks de Cucumber
 * no expone la excepción, y el triage la necesita. Los eventos se emiten en el mismo hilo del escenario.
 */
public class PasosListener implements ConcurrentEventListener {

    private static final int MAX_ERROR = 2500;
    private static final ThreadLocal<Ejecucion> ACTUAL = ThreadLocal.withInitial(Ejecucion::new);

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, evento -> ACTUAL.set(new Ejecucion()));
        publisher.registerHandlerFor(TestStepFinished.class, this::registrarPaso);
    }

    private void registrarPaso(TestStepFinished evento) {
        if (!(evento.getTestStep() instanceof PickleStepTestStep paso)) {
            return;
        }
        Ejecucion ejecucion = ACTUAL.get();
        String texto = paso.getStep().getKeyword() + paso.getStep().getText();
        Status estado = evento.getResult().getStatus();
        ejecucion.pasos.add(new FalloEscenario.Paso(texto, estado.name()));
        if (estado == Status.FAILED && ejecucion.pasoFallido == null) {
            ejecucion.pasoFallido = texto;
            Throwable error = evento.getResult().getError();
            ejecucion.error = error == null ? "Sin detalle de error" : resumir(error);
        }
    }

    private static String resumir(Throwable error) {
        String texto = error.getClass().getName() + ": " + error.getMessage();
        return texto.length() > MAX_ERROR ? texto.substring(0, MAX_ERROR) + "... [recortado]" : texto;
    }

    public static Ejecucion actual() {
        return ACTUAL.get();
    }

    public static final class Ejecucion {
        private final List<FalloEscenario.Paso> pasos = new ArrayList<>();
        private String pasoFallido;
        private String error;

        public List<FalloEscenario.Paso> pasos() {
            return List.copyOf(pasos);
        }

        public String pasoFallido() {
            return pasoFallido == null ? "(falló un hook, no un paso)" : pasoFallido;
        }

        public String error() {
            return error == null ? "Sin detalle de error" : error;
        }
    }
}
