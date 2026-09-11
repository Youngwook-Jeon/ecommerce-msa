package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.project.young.paymentservice.application.provider.ProviderPaymentResultOutcome;

import java.util.Objects;
import java.util.Optional;

/**
 * Outbound port to an external payment provider (Stripe, stub, later domestic PSPs).
 * <p>
 * Creating a payment session must not by itself complete the saga for async providers.
 * Immediate settlement is only for the local stub used in tests/dev.
 */
public interface PaymentProviderPort {

    ProviderPaymentSession createPayment(Payment payment);

    void refund(Payment payment, String idempotencyKey);

    /**
     * Reads a terminal PSP state when a webhook may have been lost. Empty means that the provider
     * still considers the payment non-terminal or does not support asynchronous reconciliation.
     */
    Optional<ProviderPaymentResult> retrieveTerminalResult(Payment payment);

    record ProviderPaymentResult(ProviderPaymentResultOutcome outcome, String failureReason) {
        public ProviderPaymentResult {
            Objects.requireNonNull(outcome, "outcome must not be null");
            if (!outcome.isTerminal()) {
                throw new IllegalArgumentException("Reconciled provider result must be terminal");
            }
            if (outcome == ProviderPaymentResultOutcome.SUCCEEDED && failureReason != null) {
                throw new IllegalArgumentException("Succeeded provider result must not contain failure reason");
            }
            if (outcome == ProviderPaymentResultOutcome.FINAL_FAILED
                    && (failureReason == null || failureReason.isBlank())) {
                throw new IllegalArgumentException("Final failure provider result requires failure reason");
            }
        }

        public static ProviderPaymentResult succeeded() {
            return new ProviderPaymentResult(ProviderPaymentResultOutcome.SUCCEEDED, null);
        }

        public static ProviderPaymentResult finalFailure(String failureReason) {
            return new ProviderPaymentResult(ProviderPaymentResultOutcome.FINAL_FAILED, failureReason);
        }
    }

    record ProviderPaymentSession(
            PaymentProvider provider,
            String providerPaymentId,
            String clientSecret,
            boolean settleImmediately,
            boolean success,
            String failureReason
    ) {
        public ProviderPaymentSession {
            Objects.requireNonNull(provider, "provider must not be null");
            Objects.requireNonNull(providerPaymentId, "providerPaymentId must not be null");
            Objects.requireNonNull(clientSecret, "clientSecret must not be null");
            if (providerPaymentId.isBlank()) {
                throw new IllegalArgumentException("providerPaymentId must not be blank");
            }
            if (clientSecret.isBlank()) {
                throw new IllegalArgumentException("clientSecret must not be blank");
            }
            if (settleImmediately && !success && (failureReason == null || failureReason.isBlank())) {
                throw new IllegalArgumentException("failureReason required when immediate settlement fails");
            }
            if (settleImmediately && success && failureReason != null) {
                throw new IllegalArgumentException("failureReason must be null when immediate settlement succeeds");
            }
            if (!settleImmediately && failureReason != null) {
                throw new IllegalArgumentException("failureReason must be null when settlement is async");
            }
        }

        public static ProviderPaymentSession async(
                PaymentProvider provider,
                String providerPaymentId,
                String clientSecret
        ) {
            return new ProviderPaymentSession(provider, providerPaymentId, clientSecret, false, false, null);
        }

        public static ProviderPaymentSession immediateSuccess(
                PaymentProvider provider,
                String providerPaymentId,
                String clientSecret
        ) {
            return new ProviderPaymentSession(provider, providerPaymentId, clientSecret, true, true, null);
        }

        public static ProviderPaymentSession immediateFailure(
                PaymentProvider provider,
                String providerPaymentId,
                String clientSecret,
                String failureReason
        ) {
            return new ProviderPaymentSession(
                    provider,
                    providerPaymentId,
                    clientSecret,
                    true,
                    false,
                    failureReason
            );
        }
    }
}
