package com.project.young.productservice.dataaccess.config;

/**
 * How catalog change events reach Kafka after outbox INSERT.
 */
public enum CatalogEventRelay {
    /** PostgreSQL WAL → Debezium → Kafka (JSON). */
    DEBEZIUM,
    /** In-app scheduled outbox poll → Kafka (Avro). */
    POLLING
}
