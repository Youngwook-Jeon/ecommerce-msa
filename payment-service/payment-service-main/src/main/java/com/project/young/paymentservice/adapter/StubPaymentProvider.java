package com.project.young.paymentservice.adapter;

import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.config.StubPaymentProperties;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Local synchronous provider (default). Deterministic client secret for Elements/client-secret API tests.
 */
@Component
@ConditionalOnProperty(prefix = "payment-service", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubPaymentProvider implements PaymentProviderPort {

    private final StubPaymentProperties properties;

    public StubPaymentProvider(StubPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public ProviderPaymentSession createPayment(Payment payment) {
        String providerPaymentId = "stub_pi_" + payment.getId().getValue();
        String clientSecret = "stub_secret_" + payment.getId().getValue();

        if (properties.alwaysSucceed()) {
            return ProviderPaymentSession.immediateSuccess(
                    PaymentProvider.STUB,
                    providerPaymentId,
                    clientSecret
            );
        }

        String declineFraction = properties.declineWhenFractionalPart();
        if (declineFraction != null && matchesFractionalPart(payment, declineFraction)) {
            return ProviderPaymentSession.immediateFailure(
                    PaymentProvider.STUB,
                    providerPaymentId,
                    clientSecret,
                    "Stub payment declined (fractional part matches " + declineFraction + ")."
            );
        }

        return ProviderPaymentSession.immediateSuccess(
                PaymentProvider.STUB,
                providerPaymentId,
                clientSecret
        );
    }

    private static boolean matchesFractionalPart(Payment payment, String fractionalPart) {
        BigDecimal amount = payment.getAmount().getAmount().stripTrailingZeros();
        String plain = amount.toPlainString();
        int dotIndex = plain.indexOf('.');
        if (dotIndex < 0) {
            return "0".equals(fractionalPart) || "0.00".equals(fractionalPart);
        }
        String actualFraction = plain.substring(dotIndex + 1);
        String normalizedExpected = fractionalPart.startsWith("0.")
                ? fractionalPart.substring(2)
                : fractionalPart;
        return actualFraction.equals(normalizedExpected);
    }
}
