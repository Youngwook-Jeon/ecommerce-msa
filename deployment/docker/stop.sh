#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

stop_failed=0
stop_group() {
  local description="$1"
  shift
  echo "Stopping ${description}..."
  if ! docker compose "$@"; then
    echo "Failed to stop ${description}. Continuing with the remaining services." >&2
    stop_failed=1
  fi
}

# Keep brokers available while Connect and Kafka clients shut down.
stop_group "Kafka Connect and Kafka clients" \
  -f common.yml -f kafka_cluster.yml -f kafka_connect.yml \
  stop -t 60 kafka-connect schema-registry kafka-ui

stop_group "Kafka brokers" \
  -f common.yml -f kafka_cluster.yml \
  stop -t 60 kafka-broker-1 kafka-broker-2 kafka-broker-3

stop_group "ZooKeeper" \
  -f common.yml -f zookeeper.yml \
  stop -t 60

stop_group "backing services and topic initializer" \
  -f common.yml -f init_kafka.yml -f backing_services.yml -f opensearch.yml \
  stop -t 60

if (( stop_failed != 0 )); then
  echo "Infrastructure shutdown encountered errors. Check container states with docker ps -a before restarting." >&2
  exit 1
fi

echo "All infrastructure containers have been stopped; volumes kept."
