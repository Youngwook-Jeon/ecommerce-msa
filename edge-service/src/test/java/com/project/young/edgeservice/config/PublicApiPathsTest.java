package com.project.young.edgeservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiPathsTest {

    @Test
    @DisplayName("isPaymentStripeWebhook matches gateway Stripe webhook path")
    void isPaymentStripeWebhook_matches() {
        assertThat(PublicApiPaths.isPaymentStripeWebhook("/api/v1/payment_service/webhooks/stripe")).isTrue();
        assertThat(PublicApiPaths.isPaymentStripeWebhook("/api/v1/payment_service/payments/orders/x/client-secret")).isFalse();
        assertThat(PublicApiPaths.isPaymentStripeWebhook("/webhooks/stripe")).isFalse();
    }

    @Test
    @DisplayName("payment path helpers follow gateway prefix convention")
    void paymentPathHelpers() {
        assertThat(PublicApiPaths.paymentStripeWebhook("v1"))
                .isEqualTo("/api/v1/payment_service/webhooks/stripe");
        assertThat(PublicApiPaths.paymentClientSecret("v1"))
                .isEqualTo("/api/v1/payment_service/payments/orders/*/client-secret");
    }
}
