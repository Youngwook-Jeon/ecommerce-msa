#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to register Debezium connectors." >&2
  exit 1
fi

"${SCRIPT_DIR}/wait-for-kafka-connect.sh"

shopt -s nullglob
configs=("${DOCKER_DIR}"/connectors/*-connector.json)
if [[ ${#configs[@]} -eq 0 ]]; then
  echo "No connector definitions found in ${DOCKER_DIR}/connectors" >&2
  exit 1
fi

# Immediately after create/update, /status can 404 briefly — retry before failing.
print_connector_status() {
  local name="$1"
  local attempts="${2:-20}"
  local i body http
  for ((i = 1; i <= attempts; i++)); do
    body="$(curl -s -w "\n%{http_code}" "${CONNECT_URL}/connectors/${name}/status" 2>/dev/null || true)"
    http="$(printf '%s\n' "${body}" | tail -n 1)"
    body="$(printf '%s\n' "${body}" | sed '$d')"
    if [[ "${http}" == "200" ]]; then
      printf '%s\n' "${body}" | jq .
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for connector status: ${name} (last HTTP ${http:-none})" >&2
  if [[ -n "${body}" ]]; then
    printf '%s\n' "${body}" >&2
  fi
  return 1
}

for config_file in "${configs[@]}"; do
  connector_name="$(jq -r '.name' "${config_file}")"
  if curl -sf "${CONNECT_URL}/connectors/${connector_name}" >/dev/null 2>&1; then
    echo "Updating connector ${connector_name}"
    curl -sf -X PUT "${CONNECT_URL}/connectors/${connector_name}/config" \
      -H "Content-Type: application/json" \
      -d "$(jq -c '.config' "${config_file}")"
  else
    echo "Creating connector ${connector_name}"
    curl -sf -X POST "${CONNECT_URL}/connectors" \
      -H "Content-Type: application/json" \
      -d @"${config_file}"
  fi
  echo
  echo "Connector status (${connector_name}):"
  print_connector_status "${connector_name}"
  echo
done

echo "All Debezium connectors registered."
curl -sf "${CONNECT_URL}/connectors" | jq .
