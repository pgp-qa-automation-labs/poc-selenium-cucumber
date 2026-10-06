#!/usr/bin/env bash
# Publica las capturas del triage en la rama de evidencias, en una carpeta por ejecución con un índice legible:
#   <fecha>_run-<número>_<rama>/
#     README.md                         ejecución, commit y tabla de capturas con su diagnóstico
#     <huella>_<escenario>.png
# Variables: GH_TOKEN, RAMA_EVIDENCIAS, CARPETA_EVIDENCIAS y las que define GitHub Actions.
set -euo pipefail

CAPTURAS="$GITHUB_WORKSPACE/target/triage/capturas"
REPORTE="$GITHUB_WORKSPACE/target/triage/triage-report.json"
DIR="$RUNNER_TEMP/evidencias"

git init -q "$DIR"
cd "$DIR"
git remote add origin "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
if git fetch -q --depth 1 origin "$RAMA_EVIDENCIAS" 2>/dev/null; then
  git checkout -q -b "$RAMA_EVIDENCIAS" FETCH_HEAD
else
  git checkout -q --orphan "$RAMA_EVIDENCIAS"
  cat > README.md <<'EOF'
# Evidencias del triage

Capturas de los fallos diagnosticados por el pipeline de la rama `feature/agentes-ia`.
Rama generada automáticamente: no contiene código y no se mezcla con las demás ramas.

Cada carpeta corresponde a una ejecución del pipeline: `<fecha>_run-<número>_<rama>`.
El número de run es el mismo que se ve en la pestaña Actions, y el `README.md` de cada carpeta
enlaza la ejecución y explica qué diagnóstico corresponde a cada captura.
EOF
fi

mkdir -p "$CARPETA_EVIDENCIAS"
cp "$CAPTURAS"/*.png "$CARPETA_EVIDENCIAS"/

URL_RUN="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
{
  echo "# Run #${GITHUB_RUN_NUMBER} · $(date -u +%F) · ${GITHUB_REF_NAME}"
  echo
  echo "| | |"
  echo "|---|---|"
  echo "| Ejecución | [Ver en Actions](${URL_RUN}) |"
  echo "| Rama / commit | \`${GITHUB_REF_NAME}\` / \`${GITHUB_SHA:0:7}\` |"
  echo "| Disparador | ${GITHUB_EVENT_NAME} |"
  echo "| Fecha (UTC) | $(date -u '+%F %T') |"
  echo
  echo "## Capturas"
  echo
  echo "| Captura | Escenario | Diagnóstico | Huella |"
  echo "|---|---|---|---|"
  # Si la tabla no se pudiera generar, las capturas se publican igual
  jq -r '.[] | select(.capturaArchivo != null)
         | "| [\(.capturaArchivo)](\(.capturaArchivo)) | \(.escenario) | \(.categoria) · \(.severidad) — \(.titulo) | `\(.huella)` |"' "$REPORTE" \
    || echo "| (no se pudo generar la tabla: revisa triage-report.json en los artefactos) | | | |"
} > "$CARPETA_EVIDENCIAS/README.md"

git add -A
git -c user.name="github-actions[bot]" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
  commit -qm "Evidencias del run #${GITHUB_RUN_NUMBER} (${GITHUB_REF_NAME})"
git push -q origin "$RAMA_EVIDENCIAS"
echo "Capturas publicadas en la rama $RAMA_EVIDENCIAS, carpeta $CARPETA_EVIDENCIAS"
