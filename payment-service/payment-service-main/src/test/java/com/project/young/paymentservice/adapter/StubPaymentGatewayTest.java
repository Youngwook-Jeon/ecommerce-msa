package com.project.young.paymentservice.adapter;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.port.output.PaymentGatewayResult;
import com.project.young.paymentservice.config.StubPaymentProperties;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StubPaymentGatewayTest {

    private static final Payment SAMPLE_PAYMENT = Payment.createPending(
            new PaymentId(UUID.randomUUID()),
            new OrderId(UUID.randomUUID()),
            new UserId("user-1"),
            new Money(new BigDecimal("25.00"))
    );

    @Test
    @DisplayName("alwaysSucceed=true면 항상 성공")
    void process_whenAlwaysSucceed_returnsSuccess() {
        StubPaymentGateway gateway = new StubPaymentGateway(new StubPaymentProperties(true, "0.99"));

        PaymentGatewayResult result = gateway.process(SAMPLE_PAYMENT);

        assertThat(result.success()).isTrue();
    }

    @Test
    @DisplayName("alwaysSucceed=false이고 fractional part가 0.99면 실패")
    void process_whenFractionMatches_declines() {
        StubPaymentGateway gateway = new StubPaymentGateway(new StubPaymentProperties(false, "0.99"));
        Payment payment = Payment.createPending(
                new PaymentId(UUID.randomUUID()),
                new OrderId(UUID.randomUUID()),
                new UserId("user-1"),
                new Money(new BigDecimal("49.99"))
        );

        PaymentGatewayResult result = gateway.process(payment);

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).contains("0.99");
    }

    @Test
    @DisplayName("alwaysSucceed=false이고 fractional part가 다르면 성공")
    void process_whenFractionDoesNotMatch_succeeds() {
        StubPaymentGateway gateway = new StubPaymentGateway(new StubPaymentProperties(false, "0.99"));

        PaymentGatewayResult result = gateway.process(SAMPLE_PAYMENT);

        assertThat(result.success()).isTrue();
    }
}
