package cl.guzman.automation.healing;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Respuesta estructurada que devuelve la IA. El SDK deriva el JSON Schema de este record,
 * así que las descripciones de cada campo forman parte de las instrucciones al modelo.
 */
public record SugerenciaLocator(
        @JsonPropertyDescription("Clasificación del cambio que sufrió el elemento")
        TipoCambio tipoCambio,

        @JsonPropertyDescription("Tipo de selector propuesto. Vacío si tipoCambio no es COSMETICO")
        Estrategia estrategia,

        @JsonPropertyDescription("Selector CSS o XPath que ubica el elemento en el HTML entregado. Vacío si tipoCambio no es COSMETICO")
        String selector,

        @JsonPropertyDescription("Confianza de 0 a 100 en que el selector apunta al mismo elemento funcional")
        int confianza,

        @JsonPropertyDescription("Explicación breve en español: qué cambió en el elemento y por qué el selector propuesto es el correcto")
        String razon) {

    public enum TipoCambio {
        /** El elemento sigue existiendo con la misma función; solo cambió cómo se identifica (clases, id, estructura). */
        COSMETICO,
        /** El elemento existe pero cambió su propósito o comportamiento: el flujo funcional se ve afectado. */
        FUNCIONAL,
        /** No hay un elemento visible que cumpla la función descrita. */
        NO_ENCONTRADO
    }

    public enum Estrategia {
        CSS,
        XPATH,
        NINGUNA
    }
}
