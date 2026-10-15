@login
Feature: Login de clientes
  Como cliente de Guzmán Corretaje
  Quiero iniciar sesión en el portal de clientes
  Para gestionar las propiedades que me interesan

  # Cada fila usa un cliente nuevo: el fixture @login genera usuario y contraseña aleatorios (Datafaker)
  # y lo registra por API antes del escenario.
  Scenario Outline: Un cliente que <intencion> inicia sesión en el portal
    Given que existe un cliente nuevo que "<intencion>"
    When inicia sesión con su usuario y contraseña
    Then ve el portal de clientes con su nombre

    Examples:
      | intencion                              |
      | quiere comprar una propiedad           |
      | consulta por una propiedad             |
      | consulta por una propiedad en arriendo |
