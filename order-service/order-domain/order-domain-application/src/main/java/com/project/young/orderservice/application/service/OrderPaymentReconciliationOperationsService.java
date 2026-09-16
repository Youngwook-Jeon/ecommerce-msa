package com.project.young.orderservice.application.service;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.dto.ManualOrderPaymentReconciliationOperationCommand;
import com.project.young.orderservice.application.dto.OrderPaymentReconciliationOperationAuditView;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.exception.OrderPaymentReconciliationOperationException;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationOperationAuditPort;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationManualOperation;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Explicit, audited operations for reconciliation records that exhausted automatic retries. */
@Service
public class OrderPaymentReconciliationOperationsService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentReconciliationOperationsService.class);

    private final OrderPaymentReconciliationFailurePort failures;
    private final OrderPaymentReconciliationOperationAuditPort audits;
    private final PaymentStatusQueryPort paymentStatuses;
    private final OrderApplicationService orders;
    private final SagaCompensationApplicationService compensations;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public OrderPaymentReconciliationOperationsService(
            OrderPaymentReconciliationFailurePort failures,
            OrderPaymentReconciliationOperationAuditPort audits,
            PaymentStatusQueryPort paymentStatuses,
            OrderApplicationService orders,
            SagaCompensationApplicationService compensations,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.failures = failures;
        this.audits = audits;
        this.paymentStatuses = paymentStatuses;
        this.orders = orders;
        this.compensations = compensations;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public void replay(ManualOrderPaymentReconciliationOperationCommand command) {
        OperationContext context = context(command);
        UUID orderId = command.orderId();
        OrderPaymentReconciliationEscalationView escalation = requiredEscalation(orderId);
        Instant now = clock.instant();
        if (!failures.claimForManualReplay(orderId, now)) {
            throw unavailable(orderId);
        }

        try {
            Map<UUID, PaymentStatusSnapshot> snapshots = paymentStatuses.findByOrderIds(java.util.List.of(orderId));
            PaymentStatusSnapshot payment = snapshots.get(orderId);
            if (payment == null || payment.status() == PaymentReconciliationStatus.PENDING) {
                throw new OrderPaymentReconciliationOperationException(
                        "Payment is not terminal for manual reconciliation replay: " + orderId);
            }
            if (payment.status() == PaymentReconciliationStatus.COMPLETED) {
                orders.confirmPayment(new UserId(escalation.userId()), new OrderId(orderId));
            } else {
                orders.cancelOrder(new UserId(escalation.userId()), new OrderId(orderId));
            }
            failures.resolve(orderId);
            recordAudit(context, OrderPaymentReconciliationManualOperation.REPLAY, null, null);
            log.info("Manual payment reconciliation replay completed orderId={} paymentId={} paymentStatus={} operatorId={} requestId={}",
                    orderId, payment.paymentId(), payment.status(), context.operatorId(), context.requestId());
        } catch (RuntimeException ex) {
            failures.recordFailure(orderId, escalation.userId(), escalation.paymentId(), escalation.paymentStatus(),
                    ex.getMessage(), now, 1);
            log.warn("Manual payment reconciliation replay failed orderId={} paymentId={} operatorId={} requestId={}",
                    orderId, escalation.paymentId(), context.operatorId(), context.requestId(), ex);
            throw ex;
        }
    }

    @Transactional
    public void close(ManualOrderPaymentReconciliationOperationCommand command) {
        OperationContext context = context(command);
        UUID orderId = command.orderId();
        String reason = command.reason();
        validateReason(reason);
        if (!failures.closeManually(orderId, reason.trim(), clock.instant())) {
            throw unavailable(orderId);
        }
        recordAudit(context, OrderPaymentReconciliationManualOperation.CLOSE, reason.trim(), null);
        log.warn("Manual payment reconciliation closure recorded orderId={} operatorId={} requestId={} reason={}",
                orderId, context.operatorId(), context.requestId(), reason.trim());
    }

    @Transactional
    public UUID requestRefund(ManualOrderPaymentReconciliationOperationCommand command) {
        OperationContext context = context(command);
        UUID orderId = command.orderId();
        String reason = command.reason();
        validateReason(reason);
        OrderPaymentReconciliationEscalationView escalation = requiredEscalation(orderId);
        if (!PaymentReconciliationStatus.COMPLETED.name().equals(escalation.paymentStatus())) {
            throw new OrderPaymentReconciliationOperationException(
                    "Only a completed payment can be refunded for reconciliation: " + orderId);
        }

        UUID compensationEventId = idGenerator.generateId();
        if (!failures.claimForRefund(orderId, compensationEventId, reason.trim(), clock.instant())) {
            throw unavailable(orderId);
        }
        compensations.requestRefundForManualReconciliation(
                compensationEventId, escalation.paymentId(), orderId, escalation.userId(), reason.trim());
        recordAudit(context, OrderPaymentReconciliationManualOperation.REFUND, reason.trim(), compensationEventId);
        log.warn("Manual payment reconciliation refund requested orderId={} paymentId={} compensationEventId={} operatorId={} requestId={}",
                orderId, escalation.paymentId(), compensationEventId, context.operatorId(), context.requestId());
        return compensationEventId;
    }

    private OrderPaymentReconciliationEscalationView requiredEscalation(UUID orderId) {
        return failures.findEscalatedByOrderId(orderId)
                .orElseThrow(() -> unavailable(orderId));
    }

    private OperationContext context(ManualOrderPaymentReconciliationOperationCommand command) {
        if (command == null || command.orderId() == null) {
            throw new OrderPaymentReconciliationOperationException("orderId is required.");
        }
        if (command.operatorId() == null || command.operatorId().isBlank() || command.operatorId().length() > 128) {
            throw new OrderPaymentReconciliationOperationException("A valid operator ID is required.");
        }
        return new OperationContext(command.orderId(), command.operatorId().trim(),
                command.requestId() == null ? idGenerator.generateId() : command.requestId());
    }

    private void recordAudit(
            OperationContext context,
            OrderPaymentReconciliationManualOperation operation,
            String reason,
            UUID compensationEventId
    ) {
        audits.record(new OrderPaymentReconciliationOperationAuditView(
                idGenerator.generateId(), context.orderId(), context.operatorId(), context.requestId(), operation,
                reason, compensationEventId, clock.instant()));
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 512) {
            throw new OrderPaymentReconciliationOperationException("A reason of 1 to 512 characters is required.");
        }
    }

    private static OrderPaymentReconciliationOperationException unavailable(UUID orderId) {
        return new OrderPaymentReconciliationOperationException(
                "No actionable escalated payment reconciliation exists for order: " + orderId);
    }

    private record OperationContext(UUID orderId, String operatorId, UUID requestId) {
    }
}
