package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.RefundCompensationDltView;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.port.output.RefundCompensationDltPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundCompensationDltReplayExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void replayManualItems_claimsRefundsAndResolves() {
        RefundCompensationDltPort queue = mock(RefundCompensationDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        RefundCompensationDltView item = item();
        when(queue.findManual(100)).thenReturn(List.of(item));
        when(queue.claimForReplay(eq(item.compensationEventId()), any())).thenReturn(true);

        executor(queue, payments).replayManualItems();

        ArgumentCaptor<RefundPaymentCommand> commandCaptor = ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(payments).refundPayment(commandCaptor.capture());
        assertThat(commandCaptor.getValue()).extracting(
                RefundPaymentCommand::compensationEventId,
                RefundPaymentCommand::paymentId,
                RefundPaymentCommand::orderId
        ).containsExactly(item.compensationEventId(), item.paymentId(), item.orderId());
        verify(queue).resolve(item.compensationEventId());
        verify(queue, never()).returnToManual(eq(item.compensationEventId()), any());
    }

    @Test
    void replayManualItems_returnsFailedReplayToManual() {
        RefundCompensationDltPort queue = mock(RefundCompensationDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        RefundCompensationDltView item = item();
        when(queue.findManual(100)).thenReturn(List.of(item));
        when(queue.claimForReplay(eq(item.compensationEventId()), any())).thenReturn(true);
        when(payments.refundPayment(any())).thenThrow(new IllegalStateException("provider unavailable"));

        executor(queue, payments).replayManualItems();

        verify(queue).returnToManual(item.compensationEventId(), "provider unavailable");
        verify(queue, never()).resolve(item.compensationEventId());
    }

    @Test
    void replayManualItems_skipsItemClaimedByAnotherWorker() {
        RefundCompensationDltPort queue = mock(RefundCompensationDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        RefundCompensationDltView item = item();
        when(queue.findManual(100)).thenReturn(List.of(item));
        when(queue.claimForReplay(eq(item.compensationEventId()), any())).thenReturn(false);

        executor(queue, payments).replayManualItems();

        verify(payments, never()).refundPayment(any());
        verify(queue, never()).resolve(any());
    }

    @Test
    void replayManualItems_escalatesItemAtAttemptLimitWithoutCallingPsp() {
        RefundCompensationDltPort queue = mock(RefundCompensationDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        RefundCompensationDltView item = new RefundCompensationDltView(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW, 5);
        when(queue.findManual(100)).thenReturn(List.of(item));

        executor(queue, payments).replayManualItems();

        verify(queue).escalate(item.compensationEventId(), "Replay attempt limit exceeded: 5");
        verify(payments, never()).refundPayment(any());
        verify(queue, never()).claimForReplay(any(), any());
    }

    private static RefundCompensationDltReplayExecutor executor(
            RefundCompensationDltPort queue,
            PaymentApplicationService payments
    ) {
        return new RefundCompensationDltReplayExecutor(
                queue, payments, Clock.fixed(NOW, ZoneOffset.UTC), 300_000L, 5);
    }

    private static RefundCompensationDltView item() {
        return new RefundCompensationDltView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW, 0);
    }
}
