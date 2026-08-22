package com.project.young.paymentservice.adapter;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.ProviderPaymentSession;
import com.project.young.paymentservice.config.StubPaymentProperties;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StubPaymentProviderTest {

    private static final Payment SAMPLE_PAYMENT = Payment.createPending(
            new PaymentId(UUID.randomUUID()),
            new OrderId(UUID.randomUUID()),
            new UserId("user-1"),
            new Money(new BigDecimal("25.00"))
    );

    @Test
    @DisplayName("alwaysSucceed=true면 즉시 성공 세션을 반환한다")
    void createPayment_whenAlwaysSucceed_returnsImmediateSuccess() {
        StubPaymentProvider provider = new StubPaymentProvider(new StubPaymentProperties(true, "0.99"));

        ProviderPaymentSession session = provider.createPayment(SAMPLE_PAYMENT);

        assertThat(session.provider()).isEqualTo(PaymentProvider.STUB);
        assertThat(session.settleImmediately()).isTrue();
        assertThat(session.success()).isTrue();
        assertThat(session.clientSecret()).startsWith("stub_secret_");
        assertThat(session.providerPaymentId()).startsWith("stub_pi_");
    }

    @Test
    @DisplayName("alwaysSucceed=false이고 fractional part가 0.99면 즉시 실패")
    void createPayment_whenFractionMatches_declines() {
        StubPaymentProvider provider = new StubPaymentProvider(new StubPaymentProperties(false, "0.99"));
        Payment payment = Payment.createPending(
                new PaymentId(UUID.randomUUID()),
                new OrderId(UUID.randomUUID()),
                new UserId("user-1"),
                new Money(new BigDecimal("49.99"))
        );

        ProviderPaymentSession session = provider.createPayment(payment);

        assertThat(session.settleImmediately()).isTrue();
        assertThat(session.success()).isFalse();
        assertThat(session.failureReason()).contains("0.99");
    }

    @Test
    @DisplayName("alwaysSucceed=false이고 fractional part가 다르면 즉시 성공")
    void createPayment_whenFractionDoesNotMatch_succeeds() {
        StubPaymentProvider provider = new StubPaymentProvider(new StubPaymentProperties(false, "0.99"));

        ProviderPaymentSession session = provider.createPayment(SAMPLE_PAYMENT);

        assertThat(session.success()).isTrue();
    }
}
