package com.project.young.paymentservice.adapter;

import com.project.young.paymentservice.application.port.output.PaymentGatewayPort;
import com.project.young.paymentservice.application.port.output.PaymentGatewayResult;
import com.project.young.paymentservice.config.StubPaymentProperties;
import com.project.young.paymentservice.domain.entity.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StubPaymentGateway implements PaymentGatewayPort {

    private final StubPaymentProperties properties;

    public StubPaymentGateway(StubPaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentGatewayResult process(Payment payment) {
        if (properties.alwaysSucceed()) {
            return PaymentGatewayResult.succeeded();
        }

        String declineFraction = properties.declineWhenFractionalPart();
        if (declineFraction != null && matchesFractionalPart(payment, declineFraction)) {
            return PaymentGatewayResult.failed(
                    "Stub payment declined (fractional part matches " + declineFraction + ").");
        }

        return PaymentGatewayResult.succeeded();
    }

    private static boolean matchesFractionalPart(Payment payment, String fractionalPart) {
        BigDecimal amount = payment.getAmount().getAmount().stripTrailingZeros();
        String plain = amount.toPlainString();
        int dotIndex = plain.indexOf('.');
        if (dotIndex < 0) {
            return "0".equals(fractionalPart) || "0.00".equals(fractionalPart);
        }
        String actualFraction = plain.substring(dotIndex + 1);
        String normalizedExpected = fractionalPart.startsWith("0.") ? fractionalPart.substring(2) : fractionalPart;
        return actualFraction.equals(normalizedExpected);
    }
}
