@triage
Feature: Triage de fallos con IA
  Como equipo de QA
  Quiero que cada escenario fallido llegue con un diagnóstico de su causa probable
  Para saber rápido si es un bug, un problema del ambiente o de la automatización

  # Escenario de demostración: se espera que FALLE. El navegador bloquea las llamadas del front a la API
  # de propiedades, como si el backend estuviera caído. El triage debería identificar el problema de backend.
  # El nombre es neutral a propósito: el agente de triage no debe deducir la causa por el título.
  #   mvn test -Dcucumber.filter.tags=@api-caida
  # Escenario de demostración: se espera que FALLE. El navegador bloquea la consulta del valor de la UF
  # (/api/uf), como cuando el proveedor externo del backend no responde. El triage debería atribuirlo al backend.
  #   mvn test -Dcucumber.filter.tags=@uf-caida
  @uf-caida @manual
  Scenario: Revisar el precio en pesos de un departamento en venta en Ñuñoa
    Given que el usuario está en el home de Guzmán Corretaje
    When selecciona la operación "Comprar"
    And filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    And presiona Buscar
    And abre el detalle de la primera propiedad del listado
    Then el detalle muestra la conversión a pesos según el valor de la UF del día

  @api-caida @manual
  Scenario: Consultar el listado de departamentos en venta en Ñuñoa
    Given que el usuario está en el home de Guzmán Corretaje
    When selecciona la operación "Comprar"
    And filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    And presiona Buscar
    Then ve el listado "Propiedades en Venta" con al menos 1 resultado
