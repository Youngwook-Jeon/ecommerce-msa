package com.project.young.orderservice.messaging.error;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retry / DLT settings for saga Kafka consumers.
 */
@ConfigurationProperties(prefix = "order-service.saga-events.consumer")
public record SagaKafkaConsumerErrorProperties(
        String failureStrategy,
        Retry retry,
        Dlt dlt
) {

    public SagaKafkaConsumerErrorProperties {
        if (failureStrategy == null || failureStrategy.isBlank()) {
            failureStrategy = "retry-then-dlt";
        }
        if (retry == null) {
            retry = new Retry(3L, 1000L);
        }
        if (dlt == null) {
            dlt = new Dlt(".DLT");
        }
    }

    /**
     * @param maxAttempts Spring {@code FixedBackOff} maxAttempts: number of retries after the
     *                    first failure (e.g. 3 → up to 3 retries, then DLT)
     * @param backoffIntervalMs delay between attempts
     */
    public record Retry(long maxAttempts, long backoffIntervalMs) {
        public Retry {
            if (maxAttempts < 1L) {
                maxAttempts = 3L;
            }
            if (backoffIntervalMs < 0L) {
                backoffIntervalMs = 1000L;
            }
        }
    }

    /**
     * @param topicSuffix appended to the source topic (e.g. {@code payment.completed} → {@code payment.completed.DLT})
     */
    public record Dlt(String topicSuffix) {
        public Dlt {
            if (topicSuffix == null || topicSuffix.isBlank()) {
                topicSuffix = ".DLT";
            }
        }
    }
}
