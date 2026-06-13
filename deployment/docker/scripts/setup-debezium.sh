#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Applying Debezium PostgreSQL grants..."
"${SCRIPT_DIR}/grant-debezium-outbox.sh"

echo "Registering Debezium connector..."
"${SCRIPT_DIR}/register-debezium-connector.sh"

echo "Debezium outbox CDC setup complete."
