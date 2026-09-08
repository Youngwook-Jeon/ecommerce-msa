package com.project.young.paymentservice.messaging.error;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment-service.saga-events.consumer")
public record PaymentSagaKafkaErrorProperties(Retry retry, Dlt dlt) {

    public PaymentSagaKafkaErrorProperties {
        if (retry == null) retry = new Retry(3L, 1000L);
        if (dlt == null) dlt = new Dlt(".DLT");
    }

    public record Retry(long maxAttempts, long backoffIntervalMs) {
        public Retry { if (maxAttempts < 1L) maxAttempts = 3L; if (backoffIntervalMs < 0L) backoffIntervalMs = 1000L; }
    }

    public record Dlt(String topicSuffix) {
        public Dlt { if (topicSuffix == null || topicSuffix.isBlank()) topicSuffix = ".DLT"; }
    }
}
