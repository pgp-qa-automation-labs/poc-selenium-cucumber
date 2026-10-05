package cl.guzman.automation.triage;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.function.Supplier;

@JsonClassDescription("Registra el diagnóstico final del fallo. Llámala una sola vez, al terminar la investigación.")
public class RegistrarDiagnostico implements Supplier<String> {

    @JsonPropertyDescription("Clasificación de la causa del fallo")
    public Diagnostico.Categoria categoria;

    @JsonPropertyDescription("Impacto en el negocio: ALTA si bloquea un flujo principal para usuarios reales, BAJA si solo afecta la automatización")
    public Diagnostico.Severidad severidad;

    @JsonPropertyDescription("Área que debería atender el problema")
    public Diagnostico.Area areaResponsable;

    @JsonPropertyDescription("Título breve en español, apto para un ticket (máximo 100 caracteres)")
    public String titulo;

    @JsonPropertyDescription("Explicación en español de la causa más probable, basada en la evidencia")
    public String causaProbable;

    @JsonPropertyDescription("Hechos concretos observados que respaldan el diagnóstico (ej. 'GET /api/properties respondió 502')")
    public List<String> evidencias;

    @JsonPropertyDescription("Siguiente paso recomendado en español para resolverlo")
    public String accionRecomendada;

    @JsonPropertyDescription("Confianza de 0 a 100 en el diagnóstico")
    public int confianza;

    @Override
    public String get() {
        TriageContext contexto = TriageContext.actual();
        contexto.registrarUso("RegistrarDiagnostico", categoria + " · " + severidad + " · " + areaResponsable + " (" + confianza + "%)");
        contexto.registrar(this);
        return "Diagnóstico registrado. La investigación terminó; no llames más herramientas.";
    }
}
