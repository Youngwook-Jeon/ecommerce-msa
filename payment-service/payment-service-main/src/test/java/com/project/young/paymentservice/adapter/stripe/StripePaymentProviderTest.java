package com.project.young.paymentservice.adapter.stripe;

import com.project.young.common.domain.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
