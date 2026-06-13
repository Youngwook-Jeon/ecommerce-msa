#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
CONNECTOR_NAME="product-catalog-outbox-connector"
CONFIG_FILE="${DOCKER_DIR}/connectors/product-catalog-outbox-connector.json"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to register the Debezium connector." >&2
  exit 1
fi

"${SCRIPT_DIR}/wait-for-kafka-connect.sh"

if curl -sf "${CONNECT_URL}/connectors/${CONNECTOR_NAME}" >/dev/null 2>&1; then
  echo "Updating connector ${CONNECTOR_NAME}"
  curl -sf -X PUT "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/config" \
    -H "Content-Type: application/json" \
    -d "$(jq -c '.config' "${CONFIG_FILE}")"
else
  echo "Creating connector ${CONNECTOR_NAME}"
  curl -sf -X POST "${CONNECT_URL}/connectors" \
    -H "Content-Type: application/json" \
    -d @"${CONFIG_FILE}"
fi

echo
echo "Connector status:"
curl -sf "${CONNECT_URL}/connectors/${CONNECTOR_NAME}/status" | jq .
