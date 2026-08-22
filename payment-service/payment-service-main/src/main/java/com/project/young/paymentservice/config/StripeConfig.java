package com.project.young.paymentservice.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "payment-service", name = "provider", havingValue = "stripe")
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {

    private final StripeProperties stripeProperties;

    public StripeConfig(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @PostConstruct
    void configureStripeApiKey() {
        if (stripeProperties.apiKey() == null) {
            throw new IllegalStateException(
                    "payment-service.stripe.api-key must be set when payment-service.provider=stripe");
        }
        Stripe.apiKey = stripeProperties.apiKey();
    }
}
