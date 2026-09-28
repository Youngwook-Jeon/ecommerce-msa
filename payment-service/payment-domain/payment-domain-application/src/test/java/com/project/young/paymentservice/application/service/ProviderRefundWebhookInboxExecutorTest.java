package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;
import com.project.young.paymentservice.application.port.output.ProviderRefundWebhookInboxPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderRefundWebhookInboxExecutorTest {
    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");
    private final ProviderRefundWebhookInboxPort inbox = mock(ProviderRefundWebhookInboxPort.class);
    private final ProviderRefundObservationApplicationService observations = mock(ProviderRefundObservationApplicationService.class);
    private final ObserveProviderRefundCommand command = new ObserveProviderRefundCommand(
            "evt_1", "re_1", "pi_1", RefundState.FAILED, "bank rejected");
    private ProviderRefundWebhookInboxExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ProviderRefundWebhookInboxExecutor(inbox, observations, Clock.fixed(NOW, ZoneOffset.UTC),
                300000, 20, 30000);
        when(inbox.findReady(100, NOW, NOW.minusMillis(300000))).thenReturn(List.of(command));
    }

    @Test
    void leasedItem_isApplied() {
        when(inbox.claim(command.eventId(), NOW, NOW.minusMillis(300000))).thenReturn(true);
        when(observations.observe(command)).thenReturn(true);
        executor.reconcile();
        verify(inbox).markApplied(command.eventId(), NOW);
        verify(inbox, never()).retryOrEscalate(any(), any(), eq(20), any(), any());
    }

    @Test
    void concurrentWorkerClaim_doesNotProcessTwice() {
        executor.reconcile();
        verify(observations, never()).observe(any());
    }

    @Test
    void unmatchedClaim_isRetriedAndEventuallyEscalated() {
        when(inbox.claim(command.eventId(), NOW, NOW.minusMillis(300000))).thenReturn(true);
        executor.reconcile();
        verify(inbox).retryOrEscalate(command.eventId(), NOW, 20, NOW.plusSeconds(30), "UnmatchedRefundClaim");
        verify(inbox, never()).markApplied(any(), any());
    }

    @Test
    void processingFailure_remainsDurable() {
        when(inbox.claim(command.eventId(), NOW, NOW.minusMillis(300000))).thenReturn(true);
        when(observations.observe(command)).thenThrow(new IllegalStateException("db unavailable"));
        executor.reconcile();
        verify(inbox).retryOrEscalate(command.eventId(), NOW, 20, NOW.plusSeconds(30), IllegalStateException.class.getName());
        verify(inbox, never()).markApplied(any(), any());
    }
}
