package com.project.young.paymentservice.adapter.stripe;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.provider.ProviderPaymentResultOutcome;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentRefundRejectedException;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StripePaymentProviderTest {

    @Test
    @DisplayName("toMinorUnits: USD는 센트 단위로 변환한다")
    void toMinorUnits_usd() {
        assertThat(StripePaymentProvider.toMinorUnits(new Money(new BigDecimal("25.00")), "USD"))
                .isEqualTo(2500L);
        assertThat(StripePaymentProvider.toMinorUnits(new Money(new BigDecimal("0.99")), "USD"))
                .isEqualTo(99L);
    }

    @Test
    @DisplayName("toMinorUnits: JPY는 소수 없이 그대로 변환한다")
    void toMinorUnits_jpy() {
        assertThat(StripePaymentProvider.toMinorUnits(new Money(new BigDecimal("1000")), "JPY"))
                .isEqualTo(1000L);
    }

    @Test
    @DisplayName("toMinorUnits: 알 수 없는 currency면 IllegalArgumentException")
    void toMinorUnits_whenUnknownCurrency_throws() {
        assertThatThrownBy(() ->
                StripePaymentProvider.toMinorUnits(new Money(new BigDecimal("10")), "ZZZ")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("toTerminalResult: succeeded와 canceled만 최종 결과로 반환한다")
    void toTerminalResult_mapsOnlyTerminalStripeStatuses() {
        PaymentIntent succeeded = mock(PaymentIntent.class);
        PaymentIntent canceled = mock(PaymentIntent.class);
        PaymentIntent pending = mock(PaymentIntent.class);
        when(succeeded.getStatus()).thenReturn("succeeded");
        when(canceled.getStatus()).thenReturn("canceled");
        when(pending.getStatus()).thenReturn("requires_action");

        assertThat(StripePaymentProvider.toTerminalResult(succeeded))
                .hasValueSatisfying(result -> assertThat(result.outcome())
                        .isEqualTo(ProviderPaymentResultOutcome.SUCCEEDED));
        assertThat(StripePaymentProvider.toTerminalResult(canceled))
                .hasValueSatisfying(result -> assertThat(result.outcome())
                        .isEqualTo(ProviderPaymentResultOutcome.FINAL_FAILED));
        assertThat(StripePaymentProvider.toTerminalResult(pending)).isEmpty();
    }

    @Test
    void hasAcceptedFullRefund_acceptsOneFullRefundOnly() {
        Payment payment = mockPayment();
        Refund accepted = mock(Refund.class);
        when(accepted.getAmount()).thenReturn(2500L);
        when(accepted.getStatus()).thenReturn("succeeded");
        when(accepted.getId()).thenReturn("re_123");

        assertThat(StripePaymentProvider.hasAcceptedFullRefund(List.of(accepted), payment)).isTrue();
    }

    @Test
    void hasAcceptedFullRefund_rejectsPartialRefund() {
        Payment payment = mockPayment();
        Refund partial = mock(Refund.class);
        when(partial.getAmount()).thenReturn(1000L);

        assertThatThrownBy(() -> StripePaymentProvider.hasAcceptedFullRefund(List.of(partial), payment))
                .isInstanceOf(PaymentRefundRejectedException.class);
    }

    private static Payment mockPayment() {
        Payment payment = mock(Payment.class);
        when(payment.getAmount()).thenReturn(new Money(new BigDecimal("25.00")));
        when(payment.getCurrency()).thenReturn("USD");
        when(payment.getId()).thenReturn(new PaymentId(UUID.randomUUID()));
        return payment;
    }
}
