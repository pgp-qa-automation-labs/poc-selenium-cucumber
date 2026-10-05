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

## Estructura

```
src/main/java/cl/guzman/automation/
├── config/        ConfigReader y EnvironmentConfig
├── driver/        DriverFactory y DriverManager (ThreadLocal)
├── healing/       Locator, HealingEngine, ClaudeLocatorAdvisor, DomSnapshot, HealingReport
├── pages/         BasePage, HomePage, ResultadosPage, DetallePropiedadPage
├── components/    TarjetaPropiedad
├── model/         Propiedad
└── utils/         TextUtils, ScreenshotUtils, WarmUpUtils

src/test/java/cl/guzman/automation/
├── context/       TestContext (estado compartido vía PicoContainer)
├── hooks/         Hooks (warm-up, navegador, screenshot al fallar)
├── runners/       TestRunner (TestNG)
├── simulation/    UiChangeSimulator (cambios de UI simulados para los escenarios de demostración)
└── steps/         NavegacionSteps, BusquedaPropiedadSteps, DetallePropiedadSteps

src/test/resources/features/   escenarios Gherkin (palabras clave en inglés, pasos en español)
```

## CI
GitHub Actions (`.github/workflows/e2e.yml`) se ejecuta en cada push a `main`, en cada PR, manualmente (eligiendo el ambiente) y de lunes a viernes a las 08:00 (hora de Chile).

```
1 · Compilar ──┬──► 2 · Pruebas de regresión ──► 3 · PR de corrección de locators (solo si hubo correcciones reales)
               └──► 2 · Demo self-healing (UI simulada)   (opcional: solo al lanzarlo a mano marcando la casilla)
```

- **Demo self-healing** corre en paralelo a la regresión porque es independiente: altera la página a propósito para demostrar las reparaciones, así que se mantiene separada para no mezclarse con los resultados reales. Como consume IA sin detectar problemas nuevos, solo se ejecuta al lanzar el workflow a mano con la casilla *Ejecutar también la demo de self-healing*.

- **Regresión** corre con `-Dhealing.patchSources=true`: si el self-healing repara un locator por un cambio **real** del front, lo corrige en la Page y genera `locators.patch`.
- **PR de corrección** aplica ese parche en la rama `self-healing/correccion-locators` y abre (o actualiza) un Pull Request con el reporte. El merge siempre es manual.
- Las reparaciones de escenarios simulados nunca corrigen el código.
- El resumen de cada run muestra el reporte de self-healing, y los reportes quedan como artefactos.
- Requisito del repo: *Settings → Actions → General → Workflow permissions →* **Allow GitHub Actions to create and approve pull requests**.

## Notas
- Si tienes un `chromedriver.exe` antiguo en el PATH, Maven lo ignora (`SE_SKIP_DRIVER_IN_PATH=true` en el `pom.xml`). Si ejecutas desde el IDE sin Maven, define esa variable de entorno en la configuración de ejecución.
