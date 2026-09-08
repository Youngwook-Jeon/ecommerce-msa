package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.port.output.RefundCompensationDltPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

@Component
public class RefundCompensationDltReplayExecutor {

    private static final Logger log = LoggerFactory.getLogger(RefundCompensationDltReplayExecutor.class);
    private final RefundCompensationDltPort queue;
    private final PaymentApplicationService payments;
    private final Clock clock;
    private final long leaseMs;
    private final int maxReplayAttempts;

    public RefundCompensationDltReplayExecutor(RefundCompensationDltPort queue, PaymentApplicationService payments, Clock clock,
                                               @Value("${payment-service.saga-events.refund-dlt-replay.lease-ms:300000}") long leaseMs,
                                               @Value("${payment-service.saga-events.refund-dlt-replay.max-attempts:5}") int maxReplayAttempts) {
        this.queue = queue;
        this.payments = payments;
        this.clock = clock;
        this.leaseMs = leaseMs;
        this.maxReplayAttempts = Math.max(1, maxReplayAttempts);
    }

    @Scheduled(fixedDelayString = "${payment-service.saga-events.refund-dlt-replay.fixed-delay-ms:30000}")
    public void replayManualItems() {
        int released = queue.returnExpiredReplaysToManual(clock.instant().minusMillis(leaseMs));

        if (released > 0) log.warn("Returned expired refund DLT replay lease(s) to MANUAL count={}", released);

        for (var item : queue.findManual(100)) {
            if (item.replayAttempts() >= maxReplayAttempts) {
                queue.escalate(item.compensationEventId(), "Replay attempt limit exceeded: " + maxReplayAttempts);
                log.error("Escalated refund DLT replay after maximum attempts eventId={} paymentId={} attempts={}",
                        item.compensationEventId(), item.paymentId(), item.replayAttempts());
                continue;
            }
            if (!queue.claimForReplay(item.compensationEventId(), clock.instant())) continue;
            try {
                payments.refundPayment(new RefundPaymentCommand(item.compensationEventId(), item.paymentId(), item.orderId()));
                queue.resolve(item.compensationEventId());
                log.info("Resolved refund DLT replay eventId={} paymentId={}", item.compensationEventId(), item.paymentId());
            } catch (RuntimeException ex) {
                queue.returnToManual(item.compensationEventId(), ex.getMessage());
                log.warn("Refund DLT replay failed; returned to MANUAL eventId={} paymentId={}", item.compensationEventId(), item.paymentId(), ex);
            }
        }
    }
}
