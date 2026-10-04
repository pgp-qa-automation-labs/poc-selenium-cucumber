/*
 * Arma la configuración final de la ejecución. Es el único lugar con lógica de configuración;
 * los archivos environments/env_<ambiente>.json contienen solo datos.
 *
 * Orden de precedencia (el último gana):
 *   1. defaults de este archivo
 *   2. env_<ambiente>.json
 *   3. reglas por contexto (ej. CI)
 *   4. propiedades de sistema -Dseccion.clave=valor
 *
 * Java invoca fn(env, envJson, sysEnvJson, sysPropsJson) y espera un JSON como respuesta.
 */
function fn(env, envJson, sysEnvJson, sysPropsJson) {

  var defaults = {
    app:      { baseUrl: '', apiUrl: '' },
    browser:  { name: 'chrome', headless: false, windowWidth: 1920, windowHeight: 1080 },
    timeouts: { explicitWaitSeconds: 10, pageLoadSeconds: 30 },
    warmUp:   { enabled: false, maxSeconds: 0, pollSeconds: 5, healthPath: '/api/hora' }
  };

  var envConfig = JSON.parse(envJson);
  var sysEnv = JSON.parse(sysEnvJson);
  var sysProps = JSON.parse(sysPropsJson);

  var config = { env: env };
  Object.keys(defaults).forEach(function (seccion) {
    config[seccion] = Object.assign({}, defaults[seccion], envConfig[seccion]);
  });

  // En GitHub Actions (CI=true) no hay pantalla: siempre headless
  if (sysEnv.CI === 'true') {
    config.browser.headless = true;
  }

  aplicarOverrides(config, sysProps);

  config.app.baseUrl = conBarraFinal(config.app.baseUrl);
  config.app.apiUrl = sinBarraFinal(config.app.apiUrl);

  validar(config);

  // Secretos: solo desde variables de entorno, nunca desde los JSON versionados
  config.secrets = {
    anthropicApiKey: sysEnv.ANTHROPIC_API_KEY || ''
  };

  return JSON.stringify(config);
}

function aplicarOverrides(config, sysProps) {
  Object.keys(sysProps).forEach(function (key) {
    var partes = key.split('.');
    if (partes.length !== 2) {
      return;
    }
    var seccion = config[partes[0]];
    if (!seccion || !(partes[1] in seccion)) {
      return;
    }
    seccion[partes[1]] = convertir(sysProps[key], seccion[partes[1]]);
  });
}

function convertir(valor, actual) {
  if (typeof actual === 'boolean') {
    return valor === 'true';
  }
  if (typeof actual === 'number') {
    var numero = Number(valor);
    if (isNaN(numero)) {
      throw new Error('Se esperaba un número y se recibió "' + valor + '"');
    }
    return numero;
  }
  return valor;
}

function validar(config) {
  if (!config.app.baseUrl) {
    throw new Error('baseUrl no definida para el ambiente "' + config.env + '"');
  }
  if (config.warmUp.enabled && !config.app.apiUrl) {
    throw new Error('warmUp habilitado pero apiUrl no definida para el ambiente "' + config.env + '"');
  }
}

function conBarraFinal(url) {
  return url && url.charAt(url.length - 1) !== '/' ? url + '/' : url;
}

function sinBarraFinal(url) {
  return url ? url.replace(/\/+$/, '') : url;
}
