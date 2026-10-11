#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}/.."

opensearch_timeout="${OPENSEARCH_STARTUP_TIMEOUT_SECONDS:-180}"
if [[ ! "${opensearch_timeout}" =~ ^[1-9][0-9]*$ ]]; then
    echo "OPENSEARCH_STARTUP_TIMEOUT_SECONDS must be a positive integer." >&2
    exit 1
fi

echo "Starting OpenSearch (waiting for cluster readiness, timeout: ${opensearch_timeout}s)"
if ! docker compose -f common.yml -f opensearch.yml up --build -d --wait \
        --wait-timeout "${opensearch_timeout}" eco-opensearch; then
    echo "OpenSearch startup failed. Recent container logs:" >&2
    docker compose -f common.yml -f opensearch.yml logs --tail 50 eco-opensearch >&2 || true
    exit 1
fi
echo "OpenSearch is ready (HTTP, default host endpoint: http://localhost:9200)"
