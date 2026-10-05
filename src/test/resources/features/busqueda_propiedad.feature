@busqueda
Feature: Búsqueda de propiedades
  Como cliente de Guzmán Corretaje
  Quiero buscar propiedades según mis filtros
  Para revisar el detalle de las que me interesan

  @smoke
  Scenario: Buscar un departamento en venta en Ñuñoa y revisar su detalle
    Given que el usuario está en el home de Guzmán Corretaje
    When selecciona la operación "Comprar"
    And filtra por tipo "Departamento", región "Región Metropolitana" y comuna "Ñuñoa"
    And presiona Buscar
    Then ve el listado "Propiedades en Venta" con al menos 1 resultado
    And todas las propiedades del listado están en la comuna "Ñuñoa"
    When abre el detalle de la primera propiedad del listado
    Then el detalle muestra el mismo título, ubicación, precio y código de la propiedad seleccionada
    And el detalle muestra las características "Dormitorios" y "Baños"
    And el detalle muestra la conversión a pesos según el valor de la UF del día
