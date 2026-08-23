package com.project.young.orderservice.messaging.error;

import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

/**
 * Pluggable failure policy for saga Kafka listeners (ack mode + retries + recovery).
 * Swap implementations via {@code order-service.saga-events.consumer.failure-strategy}.
 */
public interface KafkaListenerFailureStrategy {

    /**
     * Configures ack mode and {@link org.springframework.kafka.listener.CommonErrorHandler}
     * on the given factory.
     */
    void configure(ConcurrentKafkaListenerContainerFactory<?, ?> factory);
}
