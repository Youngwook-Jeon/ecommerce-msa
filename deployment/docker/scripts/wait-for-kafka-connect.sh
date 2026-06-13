#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-60}"

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  if curl -sf "${CONNECT_URL}/connectors" >/dev/null; then
    echo "Kafka Connect is ready at ${CONNECT_URL}"
    exit 0
  fi
  echo "Waiting for Kafka Connect (${attempt}/${MAX_ATTEMPTS})..."
  sleep 2
done

echo "Kafka Connect did not become ready in time: ${CONNECT_URL}" >&2
exit 1
