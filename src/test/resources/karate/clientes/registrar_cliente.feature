Feature: Registrar un cliente por API y evaluar si cumple el criterio para iniciar sesión

  # Sin @ignore: Karate salta los features con esa etiqueta incluso al invocarlos con Runner.runFeature.
  # Lo invoca ClienteFixture desde Java (Runner.runFeature) con: apiUrl, usuario, nombre, password, aceptaPrivacidad.
  # No usa "match" para no cortar la ejecución: el resultado queda en "elegible" y "motivo",
  # y el fixture decide si el escenario de Selenium se ejecuta o se omite.

  Scenario: Registrar y evaluar
    Given url apiUrl
    And path 'api', 'auth', 'register'
    And request { username: '#(usuario)', nombre: '#(nombre)', password: '#(password)', acepta_privacidad: '#(aceptaPrivacidad)' }
    When method post

    # Criterio: la API registra al cliente y devuelve una sesión válida para ese mismo usuario
    * def status = responseStatus
    * def registrado = status >= 200 && status < 300
    * def clienteDevuelto = registrado && response.cliente ? response.cliente.username : null
    * def elegible = registrado && clienteDevuelto == usuario && !!response.token
    * def detalleError = response && response.error ? response.error : 'respuesta sin cliente o sin token'
    * def motivo = elegible ? 'Registrado por API con sesión válida (HTTP ' + status + ')' : 'No cumple el criterio: HTTP ' + status + ' - ' + detalleError
