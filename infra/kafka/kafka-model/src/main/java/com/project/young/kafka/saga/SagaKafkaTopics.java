package com.project.young.kafka.saga;

/**
 * Kafka topic names for the order-payment saga (Debezium outbox CDC relay).
 */
public final class SagaKafkaTopics {

    public static final String ORDER_CREATED = "order.created";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    private SagaKafkaTopics() {
    }
}
