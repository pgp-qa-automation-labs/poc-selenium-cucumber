@self-healing
Feature: Self-healing de locators con IA
  Como equipo de QA
  Quiero que la automatización tolere cambios cosméticos del front
  Para que un refactor de clases CSS no rompa pruebas cuyo flujo funcional sigue intacto

  @ui-cambiada
  Scenario: El flujo de búsqueda sigue funcionando tras un refactor de clases CSS del front
    Given que el usuario está en el home de Guzmán Corretaje
    When selecciona la operación "Comprar"
    And filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    And presiona Buscar
    Then ve el listado "Propiedades en Venta" con al menos 1 resultado
    When abre el detalle de la primera propiedad del listado
    Then el detalle muestra el mismo título, ubicación, precio y código de la propiedad seleccionada

  # Escenario de demostración: se espera que FALLE, porque el botón Buscar deja de estar disponible
  # y eso sí afecta el flujo; el self-healing no debe "repararlo" y el triage debe diagnosticarlo.
  # El nombre es neutral a propósito: el agente de triage no debe deducir la causa por el título.
  # Se excluye de la ejecución normal (@manual); para correrlo:
  #   mvn test -Dcucumber.filter.tags=@ui-rota
  @ui-rota @manual
  Scenario: Buscar departamentos en venta en Ñuñoa desde el buscador del home
    Given que el usuario está en el home de Guzmán Corretaje
    When selecciona la operación "Comprar"
    And filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    And presiona Buscar
    Then ve el listado "Propiedades en Venta" con al menos 1 resultado
