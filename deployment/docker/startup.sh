#!/bin/bash

echo "Starting Zookeeper"

# start zookeeper
docker compose -f common.yml -f zookeeper.yml up -d

# check zookeeper health
zookeeperCheckResult=$(echo ruok | nc localhost 2181)

while [[ ! $zookeeperCheckResult == "imok" ]]; do
  >&2 echo "Zookeeper is not running yet!"
  sleep 2
  zookeeperCheckResult=$(echo ruok | nc localhost 2181)
done
echo "Zookeeper is running"

sleep 5

echo "Starting Kafka cluster"

# start kafka
docker compose -f common.yml -f kafka_cluster.yml up -d

# check kafka health
kafkaCheckResult=$(kcat -L -b localhost:19092 | grep '3 brokers:')

while [[ ! $kafkaCheckResult == " 3 brokers:" ]]; do
  >&2 echo "Kafka cluster is not running yet!"
  sleep 2
  kafkaCheckResult=$(kcat -L -b localhost:19092 | grep '3 brokers:')
done
echo "Kafka clusters are running"

echo "Creating Kafka topics"

# start kafka init
docker compose -f common.yml -f init_kafka.yml up -d

# check topics in kafka
kafkaTopicCheckResult=$(kcat -L -b localhost:19092 | grep 'product')

while [[ $kafkaTopicCheckResult == "" ]]; do
  >&2 echo "Kafka topics are not created yet!"
  sleep 2
  kafkaTopicCheckResult=$(kcat -L -b localhost:19092 | grep 'product')
done
echo "Kafka topics are created"

# start backing services
docker compose -f common.yml -f backing_services.yml up -d

echo "Our services are up and running."