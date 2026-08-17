#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Applying Debezium PostgreSQL grants (product)..."
"${SCRIPT_DIR}/grant-debezium-outbox.sh"

echo "Applying Debezium PostgreSQL grants (order)..."
"${SCRIPT_DIR}/grant-debezium-order-outbox.sh"

echo "Applying Debezium PostgreSQL grants (payment)..."
"${SCRIPT_DIR}/grant-debezium-payment-outbox.sh"

echo "Registering Debezium connectors..."
"${SCRIPT_DIR}/register-debezium-connectors.sh"

echo "Debezium outbox CDC setup complete."
