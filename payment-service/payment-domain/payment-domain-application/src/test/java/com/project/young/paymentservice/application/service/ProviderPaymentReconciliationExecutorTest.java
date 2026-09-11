package com.project.young.paymentservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderPaymentReconciliationExecutorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final PaymentRepository paymentsRepository = mock(PaymentRepository.class);
    private final PaymentProviderPort provider = mock(PaymentProviderPort.class);
    private final PaymentApplicationService payments = mock(PaymentApplicationService.class);
    private final ProviderPaymentReconciliationExecutor executor = new ProviderPaymentReconciliationExecutor(
            paymentsRepository,
            provider,
            payments,
            Clock.fixed(NOW, ZoneOffset.UTC),
            300_000
    );

    @Test
    void reconcilePendingPayments_whenProviderSucceeded_appliesStableSyntheticEvent() {
        Payment payment = pendingStripePayment();
        when(paymentsRepository.findPendingWithProviderSessionUpdatedBefore(
                NOW.minusSeconds(300), 100)).thenReturn(List.of(payment));
        when(provider.retrieveTerminalResult(payment)).thenReturn(Optional.of(PaymentProviderPort.ProviderPaymentResult.succeeded()));
        when(payments.applyProviderPaymentResult(any())).thenReturn(true);

        executor.reconcilePendingPayments();

        verify(payments).applyProviderPaymentResult(eq(ApplyProviderPaymentResultCommand.succeeded(
                "reconciliation:STRIPE:pi_1:SUCCEEDED", PaymentProvider.STRIPE, "pi_1")));
    }

    @Test
    void reconcilePendingPayments_whenProviderStillPending_doesNotApplyResult() {
        Payment payment = pendingStripePayment();
        when(paymentsRepository.findPendingWithProviderSessionUpdatedBefore(
                NOW.minusSeconds(300), 100)).thenReturn(List.of(payment));
        when(provider.retrieveTerminalResult(payment)).thenReturn(Optional.empty());

        executor.reconcilePendingPayments();

        verify(paymentsRepository).findPendingWithProviderSessionUpdatedBefore(NOW.minusSeconds(300), 100);
        verify(payments, org.mockito.Mockito.never()).applyProviderPaymentResult(any());
    }

    private static Payment pendingStripePayment() {
        Payment payment = Payment.createPending(
                new PaymentId(UUID.randomUUID()),
                new OrderId(UUID.randomUUID()),
                new UserId("user-1"),
                new Money(new BigDecimal("10.00")),
                "USD"
        );
        payment.assignProviderSession(PaymentProvider.STRIPE, "pi_1", "secret_1");
        return payment;
    }
}
