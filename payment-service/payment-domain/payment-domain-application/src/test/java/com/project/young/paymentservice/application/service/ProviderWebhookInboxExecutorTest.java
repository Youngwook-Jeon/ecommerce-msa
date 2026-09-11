package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.port.output.ProviderWebhookInboxPort;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderWebhookInboxExecutorTest {

    private final ProviderWebhookInboxPort inbox = mock(ProviderWebhookInboxPort.class);
    private final PaymentApplicationService payments = mock(PaymentApplicationService.class);
    private final ProviderWebhookInboxExecutor executor = new ProviderWebhookInboxExecutor(
            inbox,
            payments,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC),
            300_000,
            3,
            5_000,
            300_000
    );

    @Test
    void reconcile_whenPaymentExists_appliesAndCompletesInboxItem() {
        ApplyProviderPaymentResultCommand command = ApplyProviderPaymentResultCommand.succeeded(
                "evt_1", PaymentProvider.STRIPE, "pi_1");
        when(inbox.findReady(100)).thenReturn(List.of(command));
        when(inbox.claim(command.eventId(), Instant.parse("2026-01-01T00:00:00Z"))).thenReturn(true);
        when(payments.hasProviderPayment(command)).thenReturn(true);
        when(payments.applyProviderPaymentResult(command)).thenReturn(true);

        executor.reconcile();

        verify(inbox).markApplied(command.eventId());
    }

    @Test
    void reconcile_whenPaymentIsNotAssociated_returnsItemToWaiting() {
        ApplyProviderPaymentResultCommand command = ApplyProviderPaymentResultCommand.succeeded(
                "evt_1", PaymentProvider.STRIPE, "pi_1");
        when(inbox.findReady(100)).thenReturn(List.of(command));
        when(inbox.claim(command.eventId(), Instant.parse("2026-01-01T00:00:00Z"))).thenReturn(true);
        when(payments.hasProviderPayment(command)).thenReturn(false);
        when(inbox.hasReachedAttemptLimit(command.eventId(), 3)).thenReturn(false);
        when(inbox.attempts(command.eventId())).thenReturn(1);

        executor.reconcile();

        verify(inbox).returnToWaiting(command.eventId(), "Payment is not yet associated with provider payment id",
                Instant.parse("2026-01-01T00:00:05Z"));
        verify(payments, never()).applyProviderPaymentResult(command);
    }

    @Test
    void reconcile_whenAttemptLimitReached_escalatesItem() {
        ApplyProviderPaymentResultCommand command = ApplyProviderPaymentResultCommand.succeeded(
                "evt_1", PaymentProvider.STRIPE, "pi_1");
        when(inbox.findReady(100)).thenReturn(List.of(command));
        when(inbox.claim(command.eventId(), Instant.parse("2026-01-01T00:00:00Z"))).thenReturn(true);
        when(payments.hasProviderPayment(command)).thenReturn(false);
        when(inbox.hasReachedAttemptLimit(command.eventId(), 3)).thenReturn(true);

        executor.reconcile();

        verify(inbox).escalate(command.eventId(), "Payment is not yet associated with provider payment id");
    }
}
