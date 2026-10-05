# Guzmán Corretaje — Automatización E2E

Pruebas E2E del sitio [Guzmán Corretaje](https://guzman-corretaje.vercel.app/) con **Selenium 4**, **Cucumber 7** y **TestNG**, bajo **Page Object Model**.

## Requisitos
- Java 17
- Maven 3.9+
- Chrome (o Edge). El driver lo descarga Selenium Manager automáticamente.

## Ejecución

```bash
mvn test                              # ambiente qa (por defecto)
mvn test -Denv=dev                    # localhost (front :3000, API :5000)
mvn test -Denv=prod                   # producción (aún sin URL: falla con mensaje claro)
mvn test -Dbrowser.headless=true      # sobrescribe cualquier valor seccion.clave
mvn test -Dcucumber.filter.tags=@smoke
```

Reporte HTML: `target/cucumber-reports/cucumber.html` (incluye screenshot cuando un escenario falla).

## Configuración

```
src/test/resources/config/
├── config.json               → defaults comunes a todos los ambientes
└── environments/
    ├── env_dev.json          → datos de localhost
    ├── env_qa.json           → datos de QA (preproductivo)
    └── env_prod.json         → datos de producción
```

- Los JSON contienen **solo datos**; cada `env_<env>.json` declara únicamente lo que cambia respecto de `config.json`.
- Toda la lógica (mezcla, reglas, overrides y validaciones) está en `ConfigReader.java`.
- Precedencia (el último gana): `config.json` → `env_<env>.json` → reglas por contexto (`CI=true` fuerza headless) → `-Dseccion.clave=valor`.
- Los secretos (ej. `ANTHROPIC_API_KEY`) se leen **solo desde variables de entorno**, nunca desde los JSON.
- **Ventana del navegador:** con navegador visible se maximiza; en headless (CI) usa el tamaño fijo `browser.windowWidth` × `browser.windowHeight`.
- **Warm-up:** QA usa Render free, que se duerme por inactividad. Antes de abrir el navegador se consulta `apiUrl + /api/hora` hasta que responda (máximo `warmUp.maxSeconds`), para que las esperas de la UI se mantengan cortas.

## Self-healing con IA

Cuando un locator deja de encontrar su elemento, el framework le pide a Claude que lo identifique en el HTML actual y decida si el cambio es **cosmético** (el elemento sigue ahí con la misma función: se repara y el test continúa) o **funcional** (cambió su propósito o desapareció: el test falla, como debe).

```
BasePage.click(Locator "Botón BUSCAR del buscador principal")
   ├─ selector original funciona ─────────────► sigue normal (sin llamar a la IA)
   └─ no encuentra el elemento → HealingEngine
        ├─ captura el HTML visible (sin scripts/estilos; ocultos marcados)
        ├─ Claude: nuevo selector + tipo de cambio + confianza + razón (structured outputs)
        ├─ acepta solo COSMETICO con confianza ≥ healing.minConfidence
        ├─ valida en el navegador: elemento visible y único
        └─ usa el selector reparado el resto de la ejecución y lo registra en el reporte
```

- Cada locator declara **qué es y para qué sirve** (`Locator.of(By..., "Botón BUSCAR del buscador principal")`): esa descripción es la que permite reconocerlo cuando cambia.
- Requiere `ANTHROPIC_API_KEY` como variable de entorno (en CI, como secreto del repo). **Sin la key el healing se desactiva** y las pruebas se comportan como siempre.
- Configuración en `config.json` → `healing` (`enabled`, `model`, `minConfidence`, `maxDomChars`, `requestTimeoutSeconds`), con overrides `-Dhealing.clave=valor`.
- Reporte: adjunto en cada escenario del reporte de Cucumber y en `target/healing/healing-report.{md,json}` (locator original → nuevo, tipo de cambio, confianza, razón, tokens usados).

### Escenarios de demostración (`self_healing.feature`)
Simulan cambios del front sin tocar el sitio, inyectando un script vía Chrome DevTools Protocol:

| Tag | Simulación | Resultado esperado |
|---|---|---|
| `@ui-cambiada` | Renombra 4 clases CSS (botón Buscar, título del listado, título y dirección del detalle) | ✅ La IA repara los 4 locators y el flujo pasa |
| `@ui-rota` (`@manual`) | Oculta el botón Buscar | ❌ La IA lo clasifica como NO_ENCONTRADO y el escenario falla |

```bash
mvn test -Dcucumber.filter.tags=@ui-rota
```

## Triage de fallos con IA (agente)

Cuando un escenario falla, un agente de Claude investiga la causa **con el navegador todavía abierto en el estado del fallo**. Recibe el escenario, los pasos, el error, los intentos de self-healing y la captura de pantalla, y decide por su cuenta qué más revisar:

| Herramienta | Para qué |
|---|---|
| `LeerHtml` | HTML visible actual (mismo formato reducido que el self-healing) |
| `LeerConsola` | Errores de JavaScript y peticiones fallidas en la consola del navegador |
| `LeerRed` | Peticiones de datos (fetch/XHR) que hizo el navegador y lo que recibió en ese momento (código HTTP o error de red). Detecta fallas intermitentes que una consulta posterior a la API ya no reproduce y errores que el front oculta |
| `ConsultarApi` | `GET` a la API del ambiente (solo rutas `/api/...`) para saber si el backend responde |
| `RegistrarDiagnostico` | Cierra la investigación con el diagnóstico estructurado |

El diagnóstico (categoría, severidad, área responsable, causa probable, evidencias, acción recomendada y confianza) se adjunta al escenario en el reporte de Cucumber, al resumen del pipeline y a `target/triage/triage-report.{md,json}`. Es independiente del gestor de proyectos: es el insumo para crear issues en GitHub, Jira o Azure DevOps.

- Categorías: `BUG_APLICACION`, `AMBIENTE_NO_DISPONIBLE`, `CAMBIO_FUNCIONAL_UI`, `DATOS_DE_PRUEBA`, `PROBLEMA_DEL_TEST`, `INDETERMINADO`.
- Solo se ejecuta cuando un escenario falla: sin fallos, no hay costo. Un problema del triage nunca cambia el resultado del test.
- Configuración en `config.json` → `triage` (`enabled`, `model`, `maxIterations`, `requestTimeoutSeconds`).

| Demo | Simulación | Diagnóstico esperado |
|---|---|---|
| `@api-caida` (`@manual`) | El navegador bloquea las llamadas a `/api/properties` | `AMBIENTE_NO_DISPONIBLE`, infraestructura |
| `@uf-caida` (`@manual`) | El navegador bloquea las llamadas a `/api/uf` (valor de la UF) | `AMBIENTE_NO_DISPONIBLE`, infraestructura: el front oculta la conversión a pesos sin avisar |
| `@ui-rota` (`@manual`) | El botón Buscar desaparece | `BUG_APLICACION`, frontend |

```bash
mvn test -Dcucumber.filter.tags=@api-caida
```

### Publicación de issues
Al terminar la ejecución, cada diagnóstico se publica como issue en el gestor elegido. Los tres gestores reciben **los mismos datos** (`ContenidoIssue`), cada uno en su formato:

| Gestor | Se crea | Formato | Sin duplicados con |
|---|---|---|---|
| **GitHub Issues** (por defecto) | Issue con etiquetas | Markdown | Huella oculta en el cuerpo |
| **Jira Cloud** | Issue tipo `Bug` (configurable) | ADF (Atlassian Document Format), API v3 | Etiqueta `triage-huella-<huella>` (JQL) |
| **Azure DevOps** | Work item `Bug` con *Repro Steps* y severidad | HTML, JSON Patch, API 7.1 | Tag `triage-huella-<huella>` (WIQL) |

Al lanzar el workflow a mano, la lista **Destino de los issues** permite elegir `github`, `jira`, `azuredevops`, `todos` o `ninguno` (solo vista previa de los tres). La etapa `3 · Issues de triage` tiene **un paso por gestor**; los no elegidos aparecen omitidos. En ejecuciones automáticas el destino es la variable del repo `ISSUES_DESTINO` o, si no existe, `github`.

| Gestor | Variables del repo (*Settings → Secrets and variables → Actions → Variables*) | Secretos |
|---|---|---|
| GitHub | — | — (usa el `GITHUB_TOKEN` del workflow) |
| Jira | `JIRA_BASE_URL` (ej. `https://miempresa.atlassian.net`), `JIRA_PROJECT_KEY` | `JIRA_EMAIL`, `JIRA_API_TOKEN` |
| Azure DevOps | `AZURE_DEVOPS_ORG_URL` (ej. `https://dev.azure.com/miempresa`), `AZURE_DEVOPS_PROJECT` | `AZURE_DEVOPS_PAT` (Work Items: lectura y escritura) |

**Sin credenciales, el paso no falla:** genera solo la vista previa y el resumen indica qué falta configurar.

- **Dry-run (por defecto en local y en PRs):** no envía nada; escribe en `target/issues/<gestor>/` el payload exacto (`issue-<huella>.json`) y su vista previa (`issue-<huella>.md`) para revisar qué datos se enviarían. Para otro gestor en local: `-Dissues.tracker=jira`.
- **Envío real:** en la etapa `3 · Issues de triage` de GitHub Actions (fuera de PRs), con el `GITHUB_TOKEN` temporal del workflow (permiso `issues: write`). Se puede ejecutar aparte sobre un reporte existente:
  ```bash
  mvn test-compile exec:java -Dexec.mainClass=cl.guzman.automation.issues.PublicarIssues -Dexec.args=target/triage/triage-report.json
  ```
- **Sin duplicados:** cada fallo tiene una huella (escenario + paso + categoría + ambiente). Si ya hay un issue abierto con esa huella, se agrega un comentario en vez de abrir otro.
- **Etiquetas:** `triage-ia`, `severidad:*`, `categoria:*`, `area:*` y `demo` cuando el fallo viene de una simulación (en Jira y Azure DevOps con guion: `severidad-alta`).
- **Qué se envía:** título, severidad, categoría, área, confianza, causa probable, evidencias, acción recomendada, escenario, paso, URL, ambiente, rama/commit y enlace a la ejecución. **No se envía:** la captura, el HTML completo, la consola ni ningún secreto (la captura queda en los artefactos del run enlazado).
- Se omiten diagnósticos `INDETERMINADO` o con confianza menor a `issues.minConfidence`.

Para probarlo en GitHub: *Actions → Run workflow*, elegir la rama y en **escenarios** escribir `@ui-rota` o `@api-caida`.

## Estructura

```
src/main/java/cl/guzman/automation/
├── config/        ConfigReader y EnvironmentConfig
├── driver/        DriverFactory y DriverManager (ThreadLocal)
├── healing/       Locator, HealingEngine, ClaudeLocatorAdvisor, DomSnapshot, HealingReport
├── triage/        TriageAgent, herramientas del agente, Diagnostico, TriageReport
├── issues/        IssueTracker y sus conectores (GitHub, Jira, Azure DevOps), ContenidoIssue, IssuePublisher
├── pages/         BasePage, HomePage, ResultadosPage, DetallePropiedadPage
├── components/    TarjetaPropiedad
├── model/         Propiedad
└── utils/         TextUtils, ScreenshotUtils, WarmUpUtils

src/test/java/cl/guzman/automation/
├── context/       TestContext (estado compartido vía PicoContainer)
├── hooks/         Hooks (warm-up, navegador, screenshot y triage al fallar)
├── listeners/     PasosListener (pasos y error del escenario, para el triage)
├── runners/       TestRunner (TestNG)
├── simulation/    UiChangeSimulator (cambios de UI simulados para los escenarios de demostración)
└── steps/         NavegacionSteps, BusquedaPropiedadSteps, DetallePropiedadSteps

src/test/resources/features/   escenarios Gherkin (palabras clave en inglés, pasos en español)
```

## CI
GitHub Actions (`.github/workflows/e2e.yml`) se ejecuta en cada push a `main`, en cada PR, manualmente (eligiendo el ambiente) y de lunes a viernes a las 08:00 (hora de Chile).

```
1 · Compilar ──┬──► 2 · Pruebas de regresión ──┬──► 3 · PR de corrección de locators  (si hubo correcciones reales)
               │                                └──► 3 · Issues de triage              (si hubo diagnósticos)
               └──► 2 · Demo self-healing (UI simulada)   (opcional: solo al lanzarlo a mano marcando la casilla)
```

- **Regresión** corre con `-Dhealing.patchSources=true`: si el self-healing repara un locator por un cambio **real** del front, lo corrige en la Page y genera `locators.patch`. Si un escenario falla, el agente de triage lo diagnostica (con el navegador abierto) y deja `triage-report.json`. En el log, cada análisis es una sección plegable que muestra las herramientas que usó el agente.
- **Issues de triage** corre aunque la regresión falle: lee los diagnósticos del artefacto y los publica (`PublicarIssues`). Es la única etapa con permiso para escribir issues; las pruebas solo diagnostican. En PRs queda en dry-run.
- Al lanzar el workflow a mano, el campo **escenarios** acepta una expresión de tags (ej. `@ui-rota`) para correr las demos.
- **Demo self-healing** corre en paralelo a la regresión porque es independiente: altera la página a propósito para demostrar las reparaciones, así que se mantiene separada para no mezclarse con los resultados reales. Como consume IA sin detectar problemas nuevos, solo se ejecuta al lanzar el workflow a mano con la casilla *Ejecutar también la demo de self-healing*.
- **PR de corrección** aplica ese parche en la rama `self-healing/correccion-locators` y abre (o actualiza) un Pull Request con el reporte. El merge siempre es manual.
- Las reparaciones de escenarios simulados nunca corrigen el código.
- El resumen de cada run muestra el reporte de self-healing, y los reportes quedan como artefactos.
- Requisito del repo: *Settings → Actions → General → Workflow permissions →* **Allow GitHub Actions to create and approve pull requests**.

## Notas
- Si tienes un `chromedriver.exe` antiguo en el PATH, Maven lo ignora (`SE_SKIP_DRIVER_IN_PATH=true` en el `pom.xml`). Si ejecutas desde el IDE sin Maven, define esa variable de entorno en la configuración de ejecución.
