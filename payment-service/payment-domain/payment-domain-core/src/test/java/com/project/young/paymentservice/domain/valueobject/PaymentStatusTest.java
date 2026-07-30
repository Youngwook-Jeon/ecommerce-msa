package com.project.young.paymentservice.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    @DisplayName("isTerminal: PENDING만 non-terminal")
    void isTerminal() {
        assertThat(PaymentStatus.PENDING.isTerminal()).isFalse();
        assertThat(PaymentStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: null이면 false")
    void canTransitionTo_null_false() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(null)).isFalse();
    }

    @Test
    @DisplayName("canTransitionTo: 동일 상태는 true (idempotent)")
    void canTransitionTo_sameStatus_true() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PENDING)).isTrue();
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.COMPLETED)).isTrue();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: PENDING에서 terminal로만 전이 가능")
    void canTransitionTo_fromPending() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.COMPLETED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: terminal 상태에서는 다른 상태로 전이 불가")
    void canTransitionTo_fromTerminal_false() {
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.FAILED)).isFalse();
        assertThat(PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.COMPLETED)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
    }
}
