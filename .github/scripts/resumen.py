"""Resumen del pipeline para GitHub Actions: escribe en Markdown el resultado de cada etapa,
los contratos de la API (Karate) y los escenarios de UI (Cucumber), con el motivo de los omitidos.

Uso: python resumen.py <carpeta_reportes>   (la salida va a $GITHUB_STEP_SUMMARY o a la consola)
Etapas: variables RESULTADO_COMPILAR, RESULTADO_API y RESULTADO_UI (success, failure, skipped, cancelled).
"""
import json
import os
import pathlib
import sys

ICONO = {"success": "✅", "failure": "❌", "skipped": "⏭️", "cancelled": "⚪", "passed": "✅", "failed": "❌"}
NOMBRE_RESULTADO = {"success": "OK", "failure": "Falló", "skipped": "No se ejecutó", "cancelled": "Cancelada"}
ESTADO_ESCENARIO = {"passed": "aprobado", "failed": "fallido", "skipped": "omitido"}

base = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "reportes")
etapas = [("1 · Compilar", os.environ.get("RESULTADO_COMPILAR", "")),
          ("2 · Pruebas de API (Karate)", os.environ.get("RESULTADO_API", "")),
          ("3 · UI con fixtures (Selenium)", os.environ.get("RESULTADO_UI", ""))]
md = ["## Resultado del pipeline", "", "| Etapa | Resultado |", "|---|---|"]
md += [f"| {nombre} | {ICONO.get(r, '')} {NOMBRE_RESULTADO.get(r, r or '—')} |" for nombre, r in etapas]

if etapas[1][1] == "failure":
    md += ["", "> **La UI no se ejecutó:** la API que usan los fixtures no cumplió su contrato. "
               "Revisa el reporte `karate-report`."]

# ------------------------------------------------------------------ contrato de la API (Karate)
karate = next(base.rglob("karate-summary-json.txt"), None)
if karate:
    resumen = json.loads(karate.read_text(encoding="utf-8"))
    md += ["", "### Contrato de la API (Karate)", "", "| Feature | Aprobados | Fallidos |", "|---|---|---|"]
    for f in resumen.get("featureSummary", []):
        md.append(f"| {f['name']} | {f['passedCount']} | {f['failedCount']} |")

# ------------------------------------------------------------------ escenarios de UI (Cucumber)
cucumber = next(base.rglob("cucumber.json"), None)
if cucumber:
    filas, omitidos, cuenta = [], [], {"passed": 0, "failed": 0, "skipped": 0}
    for feature in json.loads(cucumber.read_text(encoding="utf-8")):
        for esc in feature.get("elements", []):
            if esc.get("type") != "scenario":
                continue
            estados = [p["result"]["status"] for p in esc.get("steps", [])]
            estado = "failed" if "failed" in estados else "skipped" if "skipped" in estados else "passed"
            cuenta[estado] += 1
            if estado == "skipped":
                motivo = next((p["result"].get("error_message", "") for p in esc["steps"] if p["result"].get("error_message")), "")
                motivo = motivo.splitlines()[0].replace("org.testng.SkipException: ", "") if motivo else "sin motivo registrado"
                omitidos.append(f"- **{esc['name']}**: {motivo}")
            filas.append(f"| {esc['name']} | {ICONO.get(estado, '⏭️')} {ESTADO_ESCENARIO[estado]} |")
    md += ["", "### Escenarios de UI (Cucumber)", "",
           f"Aprobados: **{cuenta['passed']}** · Fallidos: **{cuenta['failed']}** · Omitidos: **{cuenta['skipped']}**",
           "", "| Escenario | Estado |", "|---|---|"] + filas
    if omitidos:
        md += ["", "#### Omitidos y por qué", "", "Los omitidos no cuentan como fallas: sus datos no cumplieron el criterio del fixture.", ""] + omitidos

salida = os.environ.get("GITHUB_STEP_SUMMARY")
texto = "\n".join(md) + "\n"
if salida:
    with open(salida, "a", encoding="utf-8") as fh:
        fh.write(texto)
else:
    sys.stdout.reconfigure(encoding="utf-8")
    print(texto)
