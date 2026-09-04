package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.PaymentRefundPort;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaCompensationExecutorTest {
    @Test
    void executesRefundWithEventIdAsIdempotencyKey() {
        SagaCompensationPort compensations = mock(SagaCompensationPort.class);
        OrderApplicationService orders = mock(OrderApplicationService.class);
        PaymentRefundPort refunds = mock(PaymentRefundPort.class);
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(compensations.findByHandlingStatus(CompensationHandlingStatus.MANUAL, 100))
                .thenReturn(List.of(view(eventId, paymentId, CompensationRecommendedAction.REFUND)));

        new SagaCompensationExecutor(compensations, orders, refunds).executePendingCompensations();

        verify(refunds).refund(paymentId, eventId);
        verify(compensations).updateHandlingStatus(eventId, CompensationHandlingStatus.REFUNDED);
    }

    private static SagaCompensationView view(UUID eventId, UUID paymentId, CompensationRecommendedAction action) {
        return new SagaCompensationView(UUID.randomUUID(), eventId, paymentId, UUID.randomUUID(), "user", "10.00",
                "USD", "payment.completed", "payment.completed.DLT", 0, 1L, "x", "x", action,
                action == CompensationRecommendedAction.REFUND ? CompensationRefundSla.IMMEDIATE : CompensationRefundSla.NONE,
                "test", CompensationHandlingStatus.MANUAL, Instant.now(), true);
    }
}
