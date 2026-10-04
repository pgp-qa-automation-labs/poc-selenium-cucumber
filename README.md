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

## Estructura

```
src/main/java/cl/guzman/automation/
├── config/        ConfigReader y EnvironmentConfig
├── driver/        DriverFactory y DriverManager (ThreadLocal)
├── pages/         BasePage, HomePage, ResultadosPage, DetallePropiedadPage
├── components/    TarjetaPropiedad
├── model/         Propiedad
└── utils/         TextUtils, ScreenshotUtils, WarmUpUtils

src/test/java/cl/guzman/automation/
├── context/       TestContext (estado compartido vía PicoContainer)
├── hooks/         Hooks (warm-up, navegador, screenshot al fallar)
├── runners/       TestRunner (TestNG)
└── steps/         NavegacionSteps, BusquedaPropiedadSteps, DetallePropiedadSteps

src/test/resources/features/   escenarios en español (# language: es)
```

## CI
GitHub Actions (`.github/workflows/e2e.yml`) ejecuta las pruebas en cada push a `main`, en cada PR y manualmente eligiendo el ambiente. El reporte queda como artefacto del run.

## Notas
- Si tienes un `chromedriver.exe` antiguo en el PATH, Maven lo ignora (`SE_SKIP_DRIVER_IN_PATH=true` en el `pom.xml`). Si ejecutas desde el IDE sin Maven, define esa variable de entorno en la configuración de ejecución.
