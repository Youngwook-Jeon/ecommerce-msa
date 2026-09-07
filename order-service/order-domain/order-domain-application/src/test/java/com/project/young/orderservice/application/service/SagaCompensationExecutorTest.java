package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.PaymentRefundCompensationStatusPort;
import com.project.young.orderservice.application.port.output.RefundRequestedOutboxPort;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaCompensationExecutorTest {
    @Test
    void confirmsRefundAfterPaymentServiceProcessesCdcEvent() {
        SagaCompensationPort compensations = mock(SagaCompensationPort.class);
        RefundRequestedOutboxPort outbox = mock(RefundRequestedOutboxPort.class);
        PaymentRefundCompensationStatusPort paymentStatus = mock(PaymentRefundCompensationStatusPort.class);
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(compensations.findByHandlingStatus(CompensationHandlingStatus.MANUAL, 100))
                .thenReturn(List.of(view(eventId, paymentId, CompensationRecommendedAction.REFUND)));
        when(outbox.existsByCompensationEventId(eventId)).thenReturn(true);
        when(paymentStatus.isProcessed(eventId)).thenReturn(true);

        reconciler(compensations, outbox, paymentStatus).reconcilePendingCompensations();

        verify(compensations).updateHandlingStatus(eventId, CompensationHandlingStatus.REFUNDED);
    }

    @Test
    void doesNotExecuteRefundWhenCdcProcessingIsMissing() {
        SagaCompensationPort compensations = mock(SagaCompensationPort.class);
        RefundRequestedOutboxPort outbox = mock(RefundRequestedOutboxPort.class);
        PaymentRefundCompensationStatusPort paymentStatus = mock(PaymentRefundCompensationStatusPort.class);
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(compensations.findByHandlingStatus(CompensationHandlingStatus.MANUAL, 100))
                .thenReturn(List.of(view(eventId, paymentId, CompensationRecommendedAction.REFUND)));
        when(outbox.existsByCompensationEventId(eventId)).thenReturn(true);
        when(paymentStatus.isProcessed(eventId)).thenReturn(false);

        reconciler(compensations, outbox, paymentStatus).reconcilePendingCompensations();

        verify(compensations, never()).updateHandlingStatus(eventId, CompensationHandlingStatus.REFUNDED);
    }

    @Test
    void doesNotQueryPaymentServiceWhenRefundOutboxIsMissing() {
        SagaCompensationPort compensations = mock(SagaCompensationPort.class);
        RefundRequestedOutboxPort outbox = mock(RefundRequestedOutboxPort.class);
        PaymentRefundCompensationStatusPort paymentStatus = mock(PaymentRefundCompensationStatusPort.class);
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(compensations.findByHandlingStatus(CompensationHandlingStatus.MANUAL, 100))
                .thenReturn(List.of(view(eventId, paymentId, CompensationRecommendedAction.REFUND)));
        when(outbox.existsByCompensationEventId(eventId)).thenReturn(false);

        reconciler(compensations, outbox, paymentStatus).reconcilePendingCompensations();

        verify(paymentStatus, never()).isProcessed(eventId);
        verify(compensations, never()).updateHandlingStatus(eventId, CompensationHandlingStatus.REFUNDED);
    }

    private static SagaCompensationExecutor reconciler(
            SagaCompensationPort compensations,
            RefundRequestedOutboxPort outbox,
            PaymentRefundCompensationStatusPort paymentStatus
    ) {
        return new SagaCompensationExecutor(
                compensations,
                outbox,
                paymentStatus,
                Clock.fixed(Instant.parse("2026-09-07T00:10:00Z"), ZoneOffset.UTC),
                1_000L
        );
    }

    private static SagaCompensationView view(UUID eventId, UUID paymentId, CompensationRecommendedAction action) {
        return new SagaCompensationView(UUID.randomUUID(), eventId, paymentId, UUID.randomUUID(), "user", "10.00",
                "USD", "payment.completed", "payment.completed.DLT", 0, 1L, "x", "x", action,
                action == CompensationRecommendedAction.REFUND ? CompensationRefundSla.IMMEDIATE : CompensationRefundSla.NONE,
                "test", CompensationHandlingStatus.MANUAL, Instant.now(), true);
    }
}
