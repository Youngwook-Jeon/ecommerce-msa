package com.project.young.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment-service.stub-payment")
public record StubPaymentProperties(
        boolean alwaysSucceed,
        String declineWhenFractionalPart
) {
    public StubPaymentProperties {
        if (declineWhenFractionalPart != null && declineWhenFractionalPart.isBlank()) {
            declineWhenFractionalPart = null;
        }
    }
}
