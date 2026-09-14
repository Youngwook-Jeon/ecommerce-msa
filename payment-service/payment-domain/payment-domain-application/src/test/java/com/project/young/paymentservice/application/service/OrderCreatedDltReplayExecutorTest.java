package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.OrderCreatedDltView;
import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import org.junit.jupiter.api.Test;

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

class OrderCreatedDltReplayExecutorTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void replayManualItems_reprocessesOrderAndResolvesQueueItem() {
        OrderCreatedDltPort queue = mock(OrderCreatedDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        OrderCreatedDltView item = item(0);
        when(queue.findManual(100)).thenReturn(List.of(item));
        when(queue.claimForReplay(eq(item.eventId()), any())).thenReturn(true);

        executor(queue, payments).replayManualItems();

        var captor = org.mockito.ArgumentCaptor.forClass(ProcessPaymentCommand.class);
        verify(payments).processPayment(captor.capture());
        assertThat(captor.getValue()).extracting(ProcessPaymentCommand::orderId, ProcessPaymentCommand::userId,
                command -> command.amount().getAmount().toPlainString(), ProcessPaymentCommand::currency)
                .containsExactly(item.orderId(), "user-1", "49.99", "USD");
        verify(queue).resolve(item.eventId());
    }

    @Test
    void replayManualItems_returnsFailedReplayToManual() {
        OrderCreatedDltPort queue = mock(OrderCreatedDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        OrderCreatedDltView item = item(0);
        when(queue.findManual(100)).thenReturn(List.of(item));
        when(queue.claimForReplay(eq(item.eventId()), any())).thenReturn(true);
        when(payments.processPayment(any())).thenThrow(new IllegalStateException("database unavailable"));

        executor(queue, payments).replayManualItems();

        verify(queue).returnToManual(item.eventId(), "database unavailable");
        verify(queue, never()).resolve(item.eventId());
    }

    @Test
    void replayManualItems_escalatesItemAtReplayLimitWithoutProcessingPayment() {
        OrderCreatedDltPort queue = mock(OrderCreatedDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        OrderCreatedDltView item = item(5);
        when(queue.findManual(100)).thenReturn(List.of(item));

        executor(queue, payments).replayManualItems();

        verify(queue).escalate(item.eventId(), "Replay attempt limit exceeded: 5");
        verify(payments, never()).processPayment(any());
    }

    private static OrderCreatedDltReplayExecutor executor(OrderCreatedDltPort queue, PaymentApplicationService payments) {
        return new OrderCreatedDltReplayExecutor(queue, payments, Clock.fixed(NOW, ZoneOffset.UTC), 300_000, 5);
    }

    private static OrderCreatedDltView item(int replayAttempts) {
        return new OrderCreatedDltView(UUID.randomUUID(), UUID.randomUUID(), "user-1", "49.99", "USD", NOW,
                replayAttempts);
    }
}
