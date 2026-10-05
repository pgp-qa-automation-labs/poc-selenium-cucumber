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
  # y eso sí afecta el flujo. Se excluye de la ejecución normal (@manual); para correrlo:
  #   mvn test -Dcucumber.filter.tags=@ui-rota
  @ui-rota @manual
  Scenario: El self-healing no oculta un cambio que rompe el flujo funcional
    Given que el usuario está en el home de Guzmán Corretaje
    When selecciona la operación "Comprar"
    And filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    And presiona Buscar
    Then ve el listado "Propiedades en Venta" con al menos 1 resultado
