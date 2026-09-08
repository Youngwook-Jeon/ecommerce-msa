package com.project.young.kafka.saga;

/**
 * Kafka topic names for the order-payment saga (Debezium outbox CDC relay).
 * Payloads are JSON; see {@link com.project.young.kafka.saga.dto} for Debezium message records.
 */
public final class SagaKafkaTopics {

    public static final String ORDER_CREATED = "order.created";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUND_REQUESTED = "payment.refund.requested";
    public static final String INVENTORY_RELEASE_REQUESTED = "inventory.release.requested";

    private SagaKafkaTopics() {
    }
}
