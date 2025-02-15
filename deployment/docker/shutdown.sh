#!/bin/bash

echo "Shutdown backing services"
docker compose -f common.yml -f backing_services.yml down

echo "Shutdown kafka cluster"
docker compose -f common.yml -f kafka_cluster.yml down

sleep 5

echo "Shutdown zookeeper"
docker compose -f common.yml -f zookeeper.yml down

sleep 5

echo "Shutdown init kafka"
docker compose -f common.yml -f init_kafka.yml down

sleep 5

echo "Deleting Kafka and Zookeeper volumes"

yes | rm -r ./volumes/kafka/data/*

yes | rm -r ./volumes/zookeeper/*

yes | rm -r ./volumes/redis/data/*

yes | rm -r ./volumes/postgresql/data/*

echo "All services have shut down."