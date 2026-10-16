@login
Feature: Login de clientes con datos preparados por un fixture
  Como cliente de Guzmán Corretaje
  Quiero iniciar sesión en el portal de clientes
  Para gestionar las propiedades que me interesan

  # La columna "perfil" es la clave que recibe el fixture (ClienteFixture). Por cada fila:
  #   1. genera un cliente con datos aleatorios (Datafaker)
  #   2. Karate lo registra por API y evalúa el criterio
  #   3. si cumple, el escenario continúa con Selenium; si no, se omite con el motivo
  Scenario Outline: Un cliente <perfil> inicia sesión en el portal
    Given que existe un cliente "<perfil>" que cumple el criterio para iniciar sesión
    When inicia sesión con su usuario y contraseña
    Then ve el portal de clientes con su nombre

    Examples: Clientes que cumplen el criterio
      | perfil                 |
      | que quiere comprar     |
      | que consulta           |
      | que consulta arriendos |

    Examples: Cliente que no cumple el criterio (el escenario se omite)
      | perfil             |
      | sin consentimiento |
