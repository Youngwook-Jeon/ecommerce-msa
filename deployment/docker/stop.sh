#!/bin/bash

echo "Stopping the Kafka cluster, Zookeeper, and other backing services..."
docker compose \
  -f common.yml \
  -f kafka_cluster.yml \
  stop -t 60

docker compose \
  -f common.yml \
  -f zookeeper.yml \
  stop -t 60

echo "Zookeeper and kafka cluster have been stopped."
docker compose \
  -f common.yml \
  -f init_kafka.yml \
  -f backing_services.yml \
  stop

echo "All services have been stopped."
