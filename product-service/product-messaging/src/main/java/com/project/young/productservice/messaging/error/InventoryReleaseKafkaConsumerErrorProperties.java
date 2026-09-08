package com.project.young.productservice.messaging.error;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Retry and DLT settings for the inventory-release saga consumer.
 */
@ConfigurationProperties(prefix = "product-service.saga-events.inventory-release.consumer")
public record InventoryReleaseKafkaConsumerErrorProperties(Retry retry, Dlt dlt) {

    public InventoryReleaseKafkaConsumerErrorProperties {
        if (retry == null) {
            retry = new Retry(3L, 1000L);
        }
        if (dlt == null) {
            dlt = new Dlt(".DLT");
        }
    }

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

    public record Dlt(String topicSuffix) {
        public Dlt {
            if (topicSuffix == null || topicSuffix.isBlank()) {
                topicSuffix = ".DLT";
            }
        }
    }
}
