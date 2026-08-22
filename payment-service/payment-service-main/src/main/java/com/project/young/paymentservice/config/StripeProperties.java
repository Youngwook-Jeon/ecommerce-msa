package com.project.young.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment-service.stripe")
public record StripeProperties(
        String apiKey,
        String webhookSecret
) {
    public StripeProperties {
        if (apiKey != null && apiKey.isBlank()) {
            apiKey = null;
        }
        if (webhookSecret != null && webhookSecret.isBlank()) {
            webhookSecret = null;
        }
    }
}
