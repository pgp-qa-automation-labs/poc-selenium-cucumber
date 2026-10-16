Feature: Contrato de la API de registro de clientes

  La API que usan los fixtures se prueba sola, antes de la UI: si no cumple su contrato,
  los escenarios de Selenium no se ejecutan.

  Background:
    * url karate.properties['api.url']
    * def GeneradorDatos = Java.type('cl.guzman.automation.utils.GeneradorDatos')
    * def nuevoCliente =
      """
      function() {
        var c = GeneradorDatos.clienteAleatorio();
        return { username: c.usuario(), nombre: c.nombre(), password: c.password() };
      }
      """

  Scenario: Un registro válido devuelve 201 con el cliente y su token
    * def cliente = nuevoCliente()
    Given path 'api', 'auth', 'register'
    And request { username: '#(cliente.username)', nombre: '#(cliente.nombre)', password: '#(cliente.password)', acepta_privacidad: true }
    When method post
    Then status 201
    And match response.token == '#string'
    And match response.cliente.username == cliente.username
    And match response !contains { password: '#notnull' }

  Scenario: Un registro sin aceptar la política de privacidad se rechaza con 400
    * def cliente = nuevoCliente()
    Given path 'api', 'auth', 'register'
    And request { username: '#(cliente.username)', nombre: '#(cliente.nombre)', password: '#(cliente.password)', acepta_privacidad: false }
    When method post
    Then status 400
    And match response.error == '#string'
    And match response !contains { token: '#notnull' }

  Scenario: Un usuario que ya existe no se puede registrar de nuevo
    * def cliente = nuevoCliente()
    Given path 'api', 'auth', 'register'
    And request { username: '#(cliente.username)', nombre: '#(cliente.nombre)', password: '#(cliente.password)', acepta_privacidad: true }
    When method post
    Then status 201

    Given path 'api', 'auth', 'register'
    And request { username: '#(cliente.username)', nombre: '#(cliente.nombre)', password: '#(cliente.password)', acepta_privacidad: true }
    When method post
    Then assert responseStatus >= 400 && responseStatus < 500
    And match response !contains { token: '#notnull' }
