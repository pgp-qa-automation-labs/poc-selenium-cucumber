#!/usr/bin/env bash
# Despierta la API del ambiente antes de las pruebas (Render free se duerme por inactividad).
# Lee la configuración desde el mismo JSON de ambiente que usa el framework.
set -euo pipefail

ENV_NAME="${1:-qa}"
CONFIG="src/test/resources/config/environments/env_${ENV_NAME}.json"

if [ "$(jq -r '.warmUp.enabled // false' "$CONFIG")" != "true" ]; then
  echo "El ambiente '${ENV_NAME}' no requiere warm-up"
  exit 0
fi

URL="$(jq -r '.app.apiUrl' "$CONFIG")/api/hora"
MAX_SECONDS="$(jq -r '.warmUp.maxSeconds // 180' "$CONFIG")"
DEADLINE=$(( $(date +%s) + MAX_SECONDS ))

echo "Esperando que ${URL} responda (máximo ${MAX_SECONDS} s)..."
until curl -fsS --max-time 20 "$URL" > /dev/null; do
  if [ "$(date +%s)" -ge "$DEADLINE" ]; then
    echo "::error::La API de '${ENV_NAME}' no respondió en ${MAX_SECONDS} s"
    exit 1
  fi
  echo "Aún no responde, reintentando en 5 s..."
  sleep 5
done
echo "API disponible"
