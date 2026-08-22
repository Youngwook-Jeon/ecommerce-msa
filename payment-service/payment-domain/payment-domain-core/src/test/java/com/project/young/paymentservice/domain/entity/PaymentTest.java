package com.project.young.paymentservice.domain.entity;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import com.project.young.paymentservice.domain.exception.PaymentStateConflictException;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final PaymentId PAYMENT_ID = new PaymentId(UUID.randomUUID());
    private static final OrderId ORDER_ID = new OrderId(UUID.randomUUID());
    private static final UserId USER_ID = new UserId("user-1");
    private static final Money AMOUNT = new Money(new BigDecimal("49.99"));

    @Test
    @DisplayName("createPending: PENDING 상태와 기본 통화 USD로 생성한다")
    void createPending_createsPendingPaymentWithDefaultCurrency() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);

        assertThat(payment.getId()).isEqualTo(PAYMENT_ID);
        assertThat(payment.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCurrency()).isEqualTo(Payment.DEFAULT_CURRENCY);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("createPending: currency를 trim/upper하고 ISO 4217로 정규화한다")
    void createPending_normalizesCurrency() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT, " eur ");

        assertThat(payment.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("createPending: amount가 0이면 PaymentDomainException")
    void createPending_whenAmountZero_throws() {
        assertThatThrownBy(() -> Payment.createPending(
                PAYMENT_ID, ORDER_ID, USER_ID, Money.ZERO))
                .isInstanceOf(PaymentDomainException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("createPending: amount가 Money.MAX를 넘으면 PaymentDomainException")
    void createPending_whenAmountExceedsMax_throws() {
        Money tooLarge = new Money(Money.MAX.getAmount().add(new BigDecimal("0.01")));

        assertThatThrownBy(() -> Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, tooLarge))
                .isInstanceOf(PaymentDomainException.class)
                .hasMessageContaining("must not exceed");
    }

    @Test
    @DisplayName("createPending: 잘못된 currency면 PaymentDomainException")
    void createPending_whenCurrencyInvalid_throws() {
        assertThatThrownBy(() -> Payment.createPending(
                PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT, "US"))
                .isInstanceOf(PaymentDomainException.class);

        assertThatThrownBy(() -> Payment.createPending(
                PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT, "ZZZ"))
                .isInstanceOf(PaymentDomainException.class)
                .hasMessageContaining("Unknown currency");
    }

    @Test
    @DisplayName("builder: COMPLETED 상태로 생성하면 PaymentDomainException")
    void builder_whenTerminalStatus_throws() {
        assertThatThrownBy(() -> Payment.builder()
                .paymentId(PAYMENT_ID)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(AMOUNT)
                .status(PaymentStatus.COMPLETED)
                .build())
                .isInstanceOf(PaymentDomainException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    @DisplayName("reconstitute: persistence 매핑은 생성 검증을 우회한다")
    void reconstitute_bypassesCreationValidation() {
        Instant now = Instant.parse("2026-06-13T08:03:10.343300Z");

        Payment payment = Payment.reconstitute(
                PAYMENT_ID,
                ORDER_ID,
                USER_ID,
                AMOUNT,
                "USD",
                PaymentStatus.COMPLETED,
                null,
                now,
                now
        );

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getCreatedAt()).isEqualTo(now);
        assertThat(payment.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("complete: PENDING에서 COMPLETED로 전이한다")
    void complete_transitionsFromPendingToCompleted() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);

        payment.complete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("complete: 이미 COMPLETED면 idempotent하게 무시한다")
    void complete_whenAlreadyCompleted_isIdempotent() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);
        payment.complete();

        payment.complete();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("fail: PENDING에서 FAILED로 전이하고 사유를 trim해 저장한다")
    void fail_transitionsFromPendingToFailed() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);

        payment.fail("  card declined  ");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("card declined");
    }

    @Test
    @DisplayName("fail: blank reason이면 IllegalArgumentException")
    void fail_whenBlankReason_throws() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);

        assertThatThrownBy(() -> payment.fail("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("complete: FAILED 상태에서는 PaymentStateConflictException을 던진다")
    void complete_whenFailed_throwsConflict() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);
        payment.fail("card declined");

        assertThatThrownBy(payment::complete)
                .isInstanceOf(PaymentStateConflictException.class)
                .hasMessageContaining("FAILED")
                .hasMessageContaining("COMPLETED");
    }

    @Test
    @DisplayName("fail: COMPLETED 상태에서는 PaymentStateConflictException을 던진다")
    void fail_whenCompleted_throwsConflict() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);
        payment.complete();

        assertThatThrownBy(() -> payment.fail("too late"))
                .isInstanceOf(PaymentStateConflictException.class);
    }

    @Test
    @DisplayName("assignProviderSession: PENDING에서 provider 세션을 붙인다")
    void assignProviderSession_attachesSessionWhilePending() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);

        payment.assignProviderSession(PaymentProvider.STRIPE, "pi_123", "pi_123_secret_abc");

        assertThat(payment.hasProviderSession()).isTrue();
        assertThat(payment.getProvider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pi_123");
        assertThat(payment.getClientSecret()).isEqualTo("pi_123_secret_abc");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("assignProviderSession: 다른 provider 세션이면 PaymentStateConflictException")
    void assignProviderSession_whenDifferentSession_throws() {
        Payment payment = Payment.createPending(PAYMENT_ID, ORDER_ID, USER_ID, AMOUNT);
        payment.assignProviderSession(PaymentProvider.STUB, "stub_pi", "stub_secret");

        assertThatThrownBy(() -> payment.assignProviderSession(
                PaymentProvider.STRIPE, "pi_other", "secret_other"))
                .isInstanceOf(PaymentStateConflictException.class);
    }
}
