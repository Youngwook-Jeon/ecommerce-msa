package com.project.young.paymentservice.application.dto.command;

import com.project.young.paymentservice.domain.valueobject.PaymentProvider;

import java.util.Objects;

/**
 * Result of a provider-side payment confirmation (e.g. Stripe webhook).
 */
public record ApplyProviderPaymentResultCommand(
        String eventId,
        PaymentProvider provider,
        String providerPaymentId,
        boolean success,
        String failureReason
) {
    public ApplyProviderPaymentResultCommand {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(providerPaymentId, "providerPaymentId must not be null");
        if (providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("providerPaymentId must not be blank");
        }
        if (!success && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("failureReason required when success is false");
        }
        if (success && failureReason != null) {
            throw new IllegalArgumentException("failureReason must be null when success is true");
        }
    }

    public static ApplyProviderPaymentResultCommand succeeded(
            String eventId,
            PaymentProvider provider,
            String providerPaymentId
    ) {
        return new ApplyProviderPaymentResultCommand(eventId, provider, providerPaymentId, true, null);
    }

    public static ApplyProviderPaymentResultCommand failed(
            String eventId,
            PaymentProvider provider,
            String providerPaymentId,
            String failureReason
    ) {
        return new ApplyProviderPaymentResultCommand(
                eventId,
                provider,
                providerPaymentId,
                false,
                failureReason
        );
    }
}
