package com.project.young.orderservice.application.service;

import com.project.young.common.application.contract.payment.PaymentReconciliationStatus;
import com.project.young.orderservice.application.dto.PendingPaymentOrderView;
import com.project.young.orderservice.application.dto.PaymentStatusSnapshot;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.port.output.PaymentStatusQueryPort;
import com.project.young.orderservice.application.port.output.PendingPaymentOrderQueryPort;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Converges stale Order projections from Payment's authoritative terminal state. */
@Component
public class OrderPaymentReconciliationExecutor {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentReconciliationExecutor.class);
    private static final int BATCH_SIZE = 100;

    private final PendingPaymentOrderQueryPort pendingOrders;
    private final PaymentStatusQueryPort paymentStatuses;
    private final OrderPaymentReconciliationFailurePort failures;
    private final OrderApplicationService orders;
    private final Clock clock;
    private final long pendingAgeMs;
    private final int maxAttempts;

    public OrderPaymentReconciliationExecutor(
            PendingPaymentOrderQueryPort pendingOrders,
            PaymentStatusQueryPort paymentStatuses,
            OrderPaymentReconciliationFailurePort failures,
            OrderApplicationService orders,
            Clock clock,
            @Value("${order-service.saga-events.payment-status-reconciliation.pending-age-ms:300000}") long pendingAgeMs,
            @Value("${order-service.saga-events.payment-status-reconciliation.max-attempts:5}") int maxAttempts
    ) {
        this.pendingOrders = pendingOrders;
        this.paymentStatuses = paymentStatuses;
        this.failures = failures;
        this.orders = orders;
        this.clock = clock;
        this.pendingAgeMs = Math.max(0, pendingAgeMs);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${order-service.saga-events.payment-status-reconciliation.fixed-delay-ms:30000}")
    public void reconcileStalePendingPayments() {
        Instant threshold = clock.instant().minusMillis(pendingAgeMs);
        List<PendingPaymentOrderView> candidates = pendingOrders.findUpdatedBefore(threshold, BATCH_SIZE);
        if (candidates.isEmpty()) {
            return;
        }

        Set<UUID> nonRetryingOrderIds = failures.findNonRetryingOrderIds(
                candidates.stream().map(PendingPaymentOrderView::orderId).toList());
        List<PendingPaymentOrderView> actionable = candidates.stream()
                .filter(order -> !nonRetryingOrderIds.contains(order.orderId()))
                .toList();
        if (actionable.isEmpty()) {
            log.warn("All stale pending-payment orders are awaiting operations action count={}", candidates.size());
            return;
        }

        Map<UUID, PaymentStatusSnapshot> statuses;
        try {
            statuses = paymentStatuses.findByOrderIds(actionable.stream()
                    .map(PendingPaymentOrderView::orderId)
                    .toList());
        } catch (RuntimeException ex) {
            log.warn("Payment status batch lookup failed; leaving orders pending count={}", actionable.size(), ex);
            return;
        }

        for (PendingPaymentOrderView order : actionable) {
            PaymentStatusSnapshot payment = statuses.get(order.orderId());
            if (payment == null || payment.status() == PaymentReconciliationStatus.PENDING) {
                continue;
            }
            reconcileTerminalPayment(order, payment);
        }
    }

    private void reconcileTerminalPayment(PendingPaymentOrderView order, PaymentStatusSnapshot payment) {
        try {
            switch (payment.status()) {
                case COMPLETED -> orders.confirmPayment(new UserId(order.userId()), new OrderId(order.orderId()));
                case FAILED -> orders.cancelOrder(new UserId(order.userId()), new OrderId(order.orderId()));
                case PENDING -> {
                    return;
                }
            }
            failures.resolve(order.orderId());
            log.info("Converged stale pending-payment orderId={} paymentId={} paymentStatus={}",
                    order.orderId(), payment.paymentId(), payment.status());
        } catch (RuntimeException ex) {
            failures.recordFailure(
                    order.orderId(), order.userId(), payment.paymentId(), payment.status().name(), ex.getMessage(),
                    clock.instant(), maxAttempts);
            log.warn("Order payment reconciliation failed; will retry or escalate orderId={} paymentId={} paymentStatus={}",
                    order.orderId(), payment.paymentId(), payment.status(), ex);
        }
    }
}
