package com.project.young.orderservice.application.service;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.exception.OrderPaymentReconciliationOperationException;
import com.project.young.orderservice.application.port.output.IdGenerator;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
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
    private final PaymentStatusQueryPort paymentStatuses;
    private final OrderApplicationService orders;
    private final SagaCompensationApplicationService compensations;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public OrderPaymentReconciliationOperationsService(
            OrderPaymentReconciliationFailurePort failures,
            PaymentStatusQueryPort paymentStatuses,
            OrderApplicationService orders,
            SagaCompensationApplicationService compensations,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.failures = failures;
        this.paymentStatuses = paymentStatuses;
        this.orders = orders;
        this.compensations = compensations;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public void replay(UUID orderId) {
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
            log.info("Manual payment reconciliation replay completed orderId={} paymentId={} paymentStatus={}",
                    orderId, payment.paymentId(), payment.status());
        } catch (RuntimeException ex) {
            failures.recordFailure(orderId, escalation.userId(), escalation.paymentId(), escalation.paymentStatus(),
                    ex.getMessage(), now, 1);
            log.warn("Manual payment reconciliation replay failed orderId={} paymentId={}",
                    orderId, escalation.paymentId(), ex);
            throw ex;
        }
    }

    @Transactional
    public void close(UUID orderId, String reason) {
        validateReason(reason);
        if (!failures.closeManually(orderId, reason.trim(), clock.instant())) {
            throw unavailable(orderId);
        }
        log.warn("Manual payment reconciliation closure recorded orderId={} reason={}", orderId, reason.trim());
    }

    @Transactional
    public UUID requestRefund(UUID orderId, String reason) {
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
        log.warn("Manual payment reconciliation refund requested orderId={} paymentId={} compensationEventId={}",
                orderId, escalation.paymentId(), compensationEventId);
        return compensationEventId;
    }

    private OrderPaymentReconciliationEscalationView requiredEscalation(UUID orderId) {
        return failures.findEscalatedByOrderId(orderId)
                .orElseThrow(() -> unavailable(orderId));
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
}
