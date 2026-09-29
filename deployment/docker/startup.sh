#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

echo "Starting Zookeeper"

# start zookeeper
docker compose -f common.yml -f zookeeper.yml up -d

# check zookeeper health
zookeeperCheckResult=$(echo ruok | nc localhost 2181 || true)

while [[ ! $zookeeperCheckResult == "imok" ]]; do
  >&2 echo "Zookeeper is not running yet!"
  sleep 2
  zookeeperCheckResult=$(echo ruok | nc localhost 2181 || true)
done
echo "Zookeeper is running"

sleep 5

echo "Starting Kafka cluster"

# start kafka (+ schema registry, kafka-ui)
docker compose -f common.yml -f kafka_cluster.yml up -d

# check kafka health
kafkaCheckResult=$(kcat -L -b localhost:19092 | grep '3 brokers:' || true)

while [[ ! $kafkaCheckResult == " 3 brokers:" ]]; do
  >&2 echo "Kafka cluster is not running yet!"
  sleep 2
  kafkaCheckResult=$(kcat -L -b localhost:19092 | grep '3 brokers:' || true)
done
echo "Kafka clusters are running"

echo "Creating Kafka topics"

# start kafka init
docker compose -f common.yml -f init_kafka.yml up -d

# check topics in kafka
kafkaTopicCheckResult=$(kcat -L -b localhost:19092 | grep 'product' || true)

while [[ $kafkaTopicCheckResult == "" ]]; do
  >&2 echo "Kafka topics are not created yet!"
  sleep 2
  kafkaTopicCheckResult=$(kcat -L -b localhost:19092 | grep 'product' || true)
done
echo "Kafka topics are created"

# start backing services
docker compose -f common.yml -f backing_services.yml up -d
bash "${SCRIPT_DIR}/scripts/wait-postgres.sh"

echo "Preparing Kafka Connect scripting libs (Debezium Filter SMT / Groovy)"

bash "${SCRIPT_DIR}/scripts/prepare-connect-scripting-libs.sh"

echo "Starting Kafka Connect (Debezium)"

docker compose -f common.yml -f kafka_cluster.yml -f kafka_connect.yml up -d

connectCheckResult=$(curl -sf http://localhost:8083/connectors 2>/dev/null || true)
while [[ $connectCheckResult == "" ]]; do
  >&2 echo "Kafka Connect is not running yet!"
  sleep 2
  connectCheckResult=$(curl -sf http://localhost:8083/connectors 2>/dev/null || true)
done
echo "Kafka Connect is running (http://localhost:8083)"

echo "Our services are up and running."
echo "Debezium outbox CDC: run ./scripts/setup-debezium.sh after product/order/payment-service Flyway (see DEBEZIUM.md)"
