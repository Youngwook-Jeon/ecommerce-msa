package com.project.young.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment-service")
public record PaymentProviderProperties(
        String provider
) {
    public PaymentProviderProperties {
        if (provider == null || provider.isBlank()) {
            provider = "stub";
        } else {
            provider = provider.trim().toLowerCase();
        }
    }

    public boolean isStub() {
        return "stub".equals(provider);
    }

    public boolean isStripe() {
        return "stripe".equals(provider);
    }
}
