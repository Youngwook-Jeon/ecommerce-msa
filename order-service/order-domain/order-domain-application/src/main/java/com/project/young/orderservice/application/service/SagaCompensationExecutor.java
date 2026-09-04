package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.PaymentRefundPort;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Redrives durable DLT compensation records. Effects are idempotent by event id. */
@Component
public class SagaCompensationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SagaCompensationExecutor.class);

    private final SagaCompensationPort sagaCompensationPort;
    private final OrderApplicationService orderApplicationService;
    private final PaymentRefundPort paymentRefundPort;

    public SagaCompensationExecutor(
            SagaCompensationPort sagaCompensationPort,
            OrderApplicationService orderApplicationService,
            PaymentRefundPort paymentRefundPort
    ) {
        this.sagaCompensationPort = sagaCompensationPort;
        this.orderApplicationService = orderApplicationService;
        this.paymentRefundPort = paymentRefundPort;
    }

    @Scheduled(fixedDelayString = "${order-service.saga-events.compensation.executor.fixed-delay-ms:5000}")
    public void executePendingCompensations() {
        var items = sagaCompensationPort.findByHandlingStatus(CompensationHandlingStatus.MANUAL, 100);
        if (!items.isEmpty()) {
            log.info("Executing {} pending saga compensation(s)", items.size());
        }
        for (SagaCompensationView item : items) {
            try {
                if (item.recommendedAction() == CompensationRecommendedAction.REPLAY) {
                    log.info("Replaying payment confirmation for compensationEventId={} orderId={}", item.eventId(), item.orderId());
                    orderApplicationService.confirmPayment(new UserId(item.userId()), new OrderId(item.orderId()));
                    sagaCompensationPort.updateHandlingStatus(item.eventId(), CompensationHandlingStatus.REPLAYED);
                    log.info("Replayed compensationEventId={} orderId={}", item.eventId(), item.orderId());
                } else if (item.recommendedAction() == CompensationRecommendedAction.REFUND && item.paymentId() != null) {
                    log.info("Refunding compensationEventId={} paymentId={} orderId={}", item.eventId(), item.paymentId(), item.orderId());
                    paymentRefundPort.refund(item.paymentId(), item.eventId());
                    sagaCompensationPort.updateHandlingStatus(item.eventId(), CompensationHandlingStatus.REFUNDED);
                    log.info("Refunded compensationEventId={} paymentId={}", item.eventId(), item.paymentId());
                } else {
                    log.warn("Cannot automate compensationEventId={} action={} paymentId={}; leaving MANUAL", item.eventId(), item.recommendedAction(), item.paymentId());
                }
            } catch (RuntimeException ex) {
                // Keep MANUAL for the next scheduled, idempotent attempt.
                log.warn("Compensation execution failed; will retry compensationEventId={} action={} orderId={}",
                        item.eventId(), item.recommendedAction(), item.orderId(), ex);
            }
        }
    }
}
