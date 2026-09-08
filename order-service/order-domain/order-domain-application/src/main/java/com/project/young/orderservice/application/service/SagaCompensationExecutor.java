package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.PaymentRefundCompensationStatusPort;
import com.project.young.orderservice.application.port.output.InventoryReleaseCompensationStatusPort;
import com.project.young.orderservice.application.port.output.InventoryReleaseRequestedOutboxPort;
import com.project.young.orderservice.application.port.output.RefundRequestedOutboxPort;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;

/**
 * Reconciles durable saga compensations; execution belongs to the corresponding CDC consumer service.
 */
@Component
public class SagaCompensationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationExecutor.class);

    private final SagaCompensationPort sagaCompensationPort;
    private final RefundRequestedOutboxPort refundRequestedOutboxPort;
    private final PaymentRefundCompensationStatusPort paymentRefundCompensationStatusPort;
    private final InventoryReleaseRequestedOutboxPort inventoryReleaseRequestedOutboxPort;
    private final InventoryReleaseCompensationStatusPort inventoryReleaseCompensationStatusPort;
    private final Clock clock;
    private final long reconciliationGracePeriodMs;

    public SagaCompensationExecutor(
            SagaCompensationPort sagaCompensationPort,
            RefundRequestedOutboxPort refundRequestedOutboxPort,
            PaymentRefundCompensationStatusPort paymentRefundCompensationStatusPort,
            InventoryReleaseRequestedOutboxPort inventoryReleaseRequestedOutboxPort,
            InventoryReleaseCompensationStatusPort inventoryReleaseCompensationStatusPort,
            Clock clock,
            @Value("${order-service.saga-events.compensation.reconciliation.grace-period-ms:300000}")
            long reconciliationGracePeriodMs
    ) {
        this.sagaCompensationPort = sagaCompensationPort;
        this.refundRequestedOutboxPort = refundRequestedOutboxPort;
        this.paymentRefundCompensationStatusPort = paymentRefundCompensationStatusPort;
        this.inventoryReleaseRequestedOutboxPort = inventoryReleaseRequestedOutboxPort;
        this.inventoryReleaseCompensationStatusPort = inventoryReleaseCompensationStatusPort;
        this.clock = clock;
        this.reconciliationGracePeriodMs = reconciliationGracePeriodMs;
    }

    @Scheduled(fixedDelayString = "${order-service.saga-events.compensation.reconciliation.fixed-delay-ms:30000}")
    public void reconcilePendingCompensations() {
        var items = sagaCompensationPort.findByHandlingStatus(CompensationHandlingStatus.MANUAL, 100);
        if (!items.isEmpty()) {
            log.debug("Reconciling {} pending saga compensation(s)", items.size());
        }
        for (SagaCompensationView item : items) {
            try {
                reconcile(item);
            } catch (RuntimeException ex) {
                log.warn(
                        "Saga compensation reconciliation lookup failed; will retry compensationEventId={} action={} orderId={}",
                        item.eventId(),
                        item.recommendedAction(),
                        item.orderId(),
                        ex
                );
            }
        }
    }

    private void reconcile(SagaCompensationView item) {
        if (item.recommendedAction() == CompensationRecommendedAction.RELEASE_INVENTORY) {
            reconcileInventoryRelease(item);
            return;
        }
        if (item.recommendedAction() != CompensationRecommendedAction.REFUND) {
            log.debug(
                    "Leaving non-refund compensation for manual handling eventId={} action={}",
                    item.eventId(),
                    item.recommendedAction()
            );
            return;
        }
        if (item.paymentId() == null) {
            log.warn("Refund compensation has no paymentId; leaving MANUAL eventId={}", item.eventId());
            return;
        }
        if (!refundRequestedOutboxPort.existsByCompensationEventId(item.eventId())) {
            log.error(
                    "Refund reconciliation found missing transactional outbox eventId={} paymentId={} orderId={}",
                    item.eventId(),
                    item.paymentId(),
                    item.orderId()
            );
            return;
        }
        if (paymentRefundCompensationStatusPort.isProcessed(item.eventId())) {
            sagaCompensationPort.updateHandlingStatus(item.eventId(), CompensationHandlingStatus.REFUNDED);
            log.info(
                    "Refund reconciliation confirmed Payment Service processing eventId={} paymentId={}",
                    item.eventId(),
                    item.paymentId()
            );
            return;
        }

        long ageMs = Math.max(0, Duration.between(item.createdAt(), clock.instant()).toMillis());
        if (ageMs >= reconciliationGracePeriodMs) {
            log.warn(
                    "Refund CDC processing is still missing after grace period eventId={} paymentId={} ageMs={}",
                    item.eventId(),
                    item.paymentId(),
                    ageMs
            );
        } else {
            log.debug(
                    "Refund CDC processing is pending eventId={} paymentId={} ageMs={}",
                    item.eventId(),
                    item.paymentId(),
                    ageMs
            );
        }
    }

    private void reconcileInventoryRelease(SagaCompensationView item) {
        if (!inventoryReleaseRequestedOutboxPort.existsByCompensationEventId(item.eventId())) {
            log.error(
                    "Inventory-release reconciliation found missing transactional outbox eventId={} orderId={}",
                    item.eventId(), item.orderId());
            return;
        }
        if (inventoryReleaseCompensationStatusPort.isProcessed(item.eventId())) {
            sagaCompensationPort.updateHandlingStatus(item.eventId(), CompensationHandlingStatus.CLOSED);
            log.info(
                    "Inventory-release reconciliation confirmed Product Service processing eventId={} orderId={}",
                    item.eventId(), item.orderId());
            return;
        }

        logPendingCdcProcessing("Inventory release", item);
    }

    private void logPendingCdcProcessing(String compensationType, SagaCompensationView item) {
        long ageMs = Math.max(0, Duration.between(item.createdAt(), clock.instant()).toMillis());
        if (ageMs >= reconciliationGracePeriodMs) {
            log.warn(
                    "{} CDC processing is still missing after grace period eventId={} orderId={} ageMs={}",
                    compensationType, item.eventId(), item.orderId(), ageMs);
        } else {
            log.debug(
                    "{} CDC processing is pending eventId={} orderId={} ageMs={}",
                    compensationType, item.eventId(), item.orderId(), ageMs);
        }
    }
}
