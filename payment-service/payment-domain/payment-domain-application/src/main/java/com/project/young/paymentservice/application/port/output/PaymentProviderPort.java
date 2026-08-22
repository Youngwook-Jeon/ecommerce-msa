package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;

import java.util.Objects;

/**
 * Outbound port to an external payment provider (Stripe, stub, later domestic PSPs).
 * <p>
 * Creating a payment session must not by itself complete the saga for async providers.
 * Immediate settlement is only for the local stub used in tests/dev.
 */
public interface PaymentProviderPort {

    ProviderPaymentSession createPayment(Payment payment);

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
