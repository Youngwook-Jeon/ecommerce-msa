package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderPaymentReconciliationOperationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void requestRefund_claimsEscalationAndCreatesDurableRefundCompensation() {
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        SagaCompensationApplicationService compensations = mock(SagaCompensationApplicationService.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        OrderPaymentReconciliationEscalationView escalation = escalation("COMPLETED");
        UUID expectedCompensationEventId = UUID.randomUUID();
        when(failures.findEscalatedByOrderId(escalation.orderId())).thenReturn(Optional.of(escalation));
        when(idGenerator.generateId()).thenReturn(expectedCompensationEventId);
        when(failures.claimForRefund(eq(escalation.orderId()), any(), eq("order cannot be confirmed"), eq(NOW)))
                .thenReturn(true);

        UUID compensationEventId = service(failures, compensations, idGenerator).requestRefund(
                escalation.orderId(), "order cannot be confirmed");

        assertThat(compensationEventId).isEqualTo(expectedCompensationEventId);
        verify(idGenerator).generateId();
        verify(compensations).requestRefundForManualReconciliation(
                compensationEventId, escalation.paymentId(), escalation.orderId(), escalation.userId(),
                "order cannot be confirmed");
    }

    @Test
    void close_marksOnlyEscalatedRecordResolved() {
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        UUID orderId = UUID.randomUUID();
        when(failures.closeManually(orderId, "resolved externally", NOW)).thenReturn(true);

        service(failures, mock(SagaCompensationApplicationService.class), mock(IdGenerator.class))
                .close(orderId, "resolved externally");

        verify(failures).closeManually(orderId, "resolved externally", NOW);
    }

    private static OrderPaymentReconciliationOperationsService service(
            OrderPaymentReconciliationFailurePort failures,
            SagaCompensationApplicationService compensations,
            IdGenerator idGenerator
    ) {
        return new OrderPaymentReconciliationOperationsService(
                failures, mock(PaymentStatusQueryPort.class), mock(OrderApplicationService.class), compensations,
                idGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OrderPaymentReconciliationEscalationView escalation(String paymentStatus) {
        return new OrderPaymentReconciliationEscalationView(
                UUID.randomUUID(), "user-1", UUID.randomUUID(), paymentStatus, 5, "inventory unavailable",
                NOW.minusSeconds(60), NOW, OrderPaymentReconciliationStatus.ESCALATED, null, null, null);
    }
}
