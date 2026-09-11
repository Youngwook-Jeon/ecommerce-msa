package com.project.young.paymentservice.application.dto.command;

import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.project.young.paymentservice.application.provider.ProviderPaymentResultOutcome;

import java.util.Objects;

/**
 * Result of a provider-side payment confirmation (e.g. Stripe webhook).
 */
public record ApplyProviderPaymentResultCommand(
        String eventId,
        PaymentProvider provider,
        String providerPaymentId,
        ProviderPaymentResultOutcome outcome,
        String failureReason
) {
    public ApplyProviderPaymentResultCommand {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(providerPaymentId, "providerPaymentId must not be null");
        if (providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("providerPaymentId must not be blank");
        }
        if (outcome != ProviderPaymentResultOutcome.SUCCEEDED
                && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException("failureReason required for failed provider result");
        }
        if (outcome == ProviderPaymentResultOutcome.SUCCEEDED && failureReason != null) {
            throw new IllegalArgumentException("failureReason must be null when provider result succeeded");
        }
    }

    public static ApplyProviderPaymentResultCommand succeeded(
            String eventId,
            PaymentProvider provider,
            String providerPaymentId
    ) {
        return new ApplyProviderPaymentResultCommand(
                eventId, provider, providerPaymentId, ProviderPaymentResultOutcome.SUCCEEDED, null);
    }

    public static ApplyProviderPaymentResultCommand paymentAttemptFailed(
            String eventId,
            PaymentProvider provider,
            String providerPaymentId,
            String failureReason
    ) {
        return new ApplyProviderPaymentResultCommand(
                eventId,
                provider,
                providerPaymentId,
                ProviderPaymentResultOutcome.ATTEMPT_FAILED,
                failureReason
        );
    }

    public static ApplyProviderPaymentResultCommand finalFailure(
            String eventId,
            PaymentProvider provider,
            String providerPaymentId,
            String failureReason
    ) {
        return new ApplyProviderPaymentResultCommand(
                eventId, provider, providerPaymentId, ProviderPaymentResultOutcome.FINAL_FAILED, failureReason);
    }

    public boolean success() {
        return outcome == ProviderPaymentResultOutcome.SUCCEEDED;
    }
}
