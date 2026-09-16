package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.dto.ManualOrderPaymentReconciliationOperationCommand;
import com.project.young.orderservice.application.dto.OrderPaymentReconciliationOperationAuditView;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationOperationAuditPort;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus;
import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationManualOperation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class OrderPaymentReconciliationOperationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T00:00:00Z");

    @Test
    void replay_recordsOperatorAndRequestAuditAfterSuccessfulConvergence() {
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        OrderPaymentReconciliationOperationAuditPort audits = mock(OrderPaymentReconciliationOperationAuditPort.class);
        PaymentStatusQueryPort paymentStatuses = mock(PaymentStatusQueryPort.class);
        OrderApplicationService orders = mock(OrderApplicationService.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        OrderPaymentReconciliationEscalationView escalation = escalation("COMPLETED");
        UUID requestId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        when(failures.findEscalatedByOrderId(escalation.orderId())).thenReturn(Optional.of(escalation));
        when(failures.claimForManualReplay(escalation.orderId(), NOW)).thenReturn(true);
        when(paymentStatuses.findByOrderIds(List.of(escalation.orderId()))).thenReturn(Map.of(
                escalation.orderId(), new PaymentStatusSnapshot(
                        escalation.paymentId(), escalation.orderId(),
                        com.project.young.common.application.contract.payment.PaymentReconciliationStatus.COMPLETED, NOW)
        ));
        when(idGenerator.generateId()).thenReturn(auditId);

        service(failures, audits, paymentStatuses, orders, mock(SagaCompensationApplicationService.class), idGenerator)
                .replay(command(escalation.orderId(), requestId, null));

        verify(failures).resolve(escalation.orderId());
        verify(audits).record(new OrderPaymentReconciliationOperationAuditView(
                auditId, escalation.orderId(), "admin-1", requestId, OrderPaymentReconciliationManualOperation.REPLAY,
                null, null, NOW));
    }

    @Test
    void requestRefund_claimsEscalationAndCreatesDurableRefundCompensation() {
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        SagaCompensationApplicationService compensations = mock(SagaCompensationApplicationService.class);
        IdGenerator idGenerator = mock(IdGenerator.class);
        OrderPaymentReconciliationOperationAuditPort audits = mock(OrderPaymentReconciliationOperationAuditPort.class);
        OrderPaymentReconciliationEscalationView escalation = escalation("COMPLETED");
        UUID requestId = UUID.randomUUID();
        UUID expectedCompensationEventId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        when(failures.findEscalatedByOrderId(escalation.orderId())).thenReturn(Optional.of(escalation));
        when(idGenerator.generateId()).thenReturn(requestId, expectedCompensationEventId, auditId);
        when(failures.claimForRefund(eq(escalation.orderId()), any(), eq("order cannot be confirmed"), eq(NOW)))
                .thenReturn(true);

        UUID compensationEventId = service(failures, compensations, idGenerator, audits).requestRefund(
                command(escalation.orderId(), null, "order cannot be confirmed"));

        assertThat(compensationEventId).isEqualTo(expectedCompensationEventId);
        verify(idGenerator, times(3)).generateId();
        verify(compensations).requestRefundForManualReconciliation(
                compensationEventId, escalation.paymentId(), escalation.orderId(), escalation.userId(),
                "order cannot be confirmed");
        verify(audits).record(new OrderPaymentReconciliationOperationAuditView(
                auditId, escalation.orderId(), "admin-1", requestId,
                OrderPaymentReconciliationManualOperation.REFUND, "order cannot be confirmed",
                compensationEventId, NOW));
    }

    @Test
    void close_marksOnlyEscalatedRecordResolved() {
        OrderPaymentReconciliationFailurePort failures = mock(OrderPaymentReconciliationFailurePort.class);
        UUID orderId = UUID.randomUUID();
        when(failures.closeManually(orderId, "resolved externally", NOW)).thenReturn(true);

        IdGenerator idGenerator = mock(IdGenerator.class);
        OrderPaymentReconciliationOperationAuditPort audits = mock(OrderPaymentReconciliationOperationAuditPort.class);
        UUID auditId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(idGenerator.generateId()).thenReturn(auditId);

        service(failures, mock(SagaCompensationApplicationService.class), idGenerator, audits)
                .close(command(orderId, requestId, "resolved externally"));

        verify(failures).closeManually(orderId, "resolved externally", NOW);
        verify(audits).record(new OrderPaymentReconciliationOperationAuditView(
                auditId, orderId, "admin-1", requestId, OrderPaymentReconciliationManualOperation.CLOSE,
                "resolved externally", null, NOW));
    }

    private static OrderPaymentReconciliationOperationsService service(
            OrderPaymentReconciliationFailurePort failures,
            SagaCompensationApplicationService compensations,
            IdGenerator idGenerator,
            OrderPaymentReconciliationOperationAuditPort audits
    ) {
        return service(failures, audits, mock(PaymentStatusQueryPort.class), mock(OrderApplicationService.class),
                compensations, idGenerator);
    }

    private static OrderPaymentReconciliationOperationsService service(
            OrderPaymentReconciliationFailurePort failures,
            OrderPaymentReconciliationOperationAuditPort audits,
            PaymentStatusQueryPort paymentStatuses,
            OrderApplicationService orders,
            SagaCompensationApplicationService compensations,
            IdGenerator idGenerator
    ) {
        return new OrderPaymentReconciliationOperationsService(
                failures, audits, paymentStatuses, orders, compensations, idGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ManualOrderPaymentReconciliationOperationCommand command(UUID orderId, UUID requestId, String reason) {
        return new ManualOrderPaymentReconciliationOperationCommand(orderId, "admin-1", requestId, reason);
    }

    private static OrderPaymentReconciliationEscalationView escalation(String paymentStatus) {
        return new OrderPaymentReconciliationEscalationView(
                UUID.randomUUID(), "user-1", UUID.randomUUID(), paymentStatus, 5, "inventory unavailable",
                NOW.minusSeconds(60), NOW, OrderPaymentReconciliationStatus.ESCALATED, null, null, null);
    }
}
