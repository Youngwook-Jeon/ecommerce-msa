package com.project.young.paymentservice.it.support;

import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Kafka/DB ITs use {@code WebEnvironment.NONE} and a fake issuer URI, so Spring cannot
 * build a JwtDecoder from OIDC metadata. Provide a local HS256 decoder for context startup.
 */
@TestConfiguration
public class PaymentIntegrationTestConfiguration {

    @Bean
    RecordingPaymentProvider recordingPaymentProvider() {
        return new RecordingPaymentProvider();
    }

    @Bean
    @Primary
    PaymentProviderPort paymentProviderPort(RecordingPaymentProvider recordingPaymentProvider) {
        return recordingPaymentProvider;
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(
                new SecretKeySpec("integration-test-secret-key-32b!!".getBytes(), "HmacSHA256")
        ).build();
    }

    /** Test PSP that records every refund idempotency key without leaving the process. */
    public static final class RecordingPaymentProvider implements PaymentProviderPort {
        private final List<String> refundIdempotencyKeys = new CopyOnWriteArrayList<>();
        private final AtomicInteger remainingRefundFailures = new AtomicInteger();

        @Override
        public ProviderPaymentSession createPayment(Payment payment) {
            String providerPaymentId = "it_pi_" + payment.getId().getValue();
            String clientSecret = "it_secret_" + payment.getId().getValue();
            if (payment.getAmount().getAmount().remainder(java.math.BigDecimal.ONE)
                    .compareTo(new java.math.BigDecimal("0.99")) == 0) {
                return ProviderPaymentSession.immediateFailure(
                        PaymentProvider.STUB, providerPaymentId, clientSecret, "Test PSP declined payment");
            }
            return ProviderPaymentSession.immediateSuccess(PaymentProvider.STUB, providerPaymentId, clientSecret);
        }

        @Override
        public void refund(Payment payment, String idempotencyKey) {
            refundIdempotencyKeys.add(idempotencyKey);
            if (remainingRefundFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw new IllegalStateException("Test PSP refund unavailable");
            }
        }

        @Override
        public Optional<ProviderPaymentResult> retrieveTerminalResult(Payment payment) {
            return Optional.empty();
        }

        public void failNextRefunds(int count) { remainingRefundFailures.set(count); }

        public void reset() {
            refundIdempotencyKeys.clear();
            remainingRefundFailures.set(0);
        }

        public List<String> refundIdempotencyKeys() { return List.copyOf(refundIdempotencyKeys); }
    }
}
