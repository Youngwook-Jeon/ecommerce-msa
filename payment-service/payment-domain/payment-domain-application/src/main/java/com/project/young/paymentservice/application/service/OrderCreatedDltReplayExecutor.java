package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class OrderCreatedDltReplayExecutor {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedDltReplayExecutor.class);

    private final OrderCreatedDltPort queue;
    private final PaymentApplicationService payments;
    private final Clock clock;
    private final long leaseMs;
    private final int maxReplayAttempts;

    public OrderCreatedDltReplayExecutor(
            OrderCreatedDltPort queue,
            PaymentApplicationService payments,
            Clock clock,
            @Value("${payment-service.saga-events.order-created-dlt-replay.lease-ms:300000}") long leaseMs,
            @Value("${payment-service.saga-events.order-created-dlt-replay.max-attempts:5}") int maxReplayAttempts
    ) {
        this.queue = queue;
        this.payments = payments;
        this.clock = clock;
        this.leaseMs = leaseMs;
        this.maxReplayAttempts = Math.max(1, maxReplayAttempts);
    }

    @Scheduled(fixedDelayString = "${payment-service.saga-events.order-created-dlt-replay.fixed-delay-ms:30000}")
    public void replayManualItems() {
        int released = queue.returnExpiredReplaysToManual(clock.instant().minusMillis(leaseMs));
        if (released > 0) {
            log.warn("Returned expired order.created DLT replay lease(s) to MANUAL count={}", released);
        }

        for (var item : queue.findManual(100)) {
            if (item.replayAttempts() >= maxReplayAttempts) {
                queue.escalate(item.eventId(), "Replay attempt limit exceeded: " + maxReplayAttempts);
                log.error("Escalated order.created DLT replay eventId={} orderId={} attempts={}",
                        item.eventId(), item.orderId(), item.replayAttempts());
                continue;
            }
            if (!queue.claimForReplay(item.eventId(), clock.instant())) {
                continue;
            }
            try {
                payments.processPayment(ProcessPaymentCommand.fromOrderCreated(
                        item.orderId(), item.userId(), item.totalAmount(), item.currency()));
                queue.resolve(item.eventId());
                log.info("Resolved order.created DLT replay eventId={} orderId={}", item.eventId(), item.orderId());
            } catch (RuntimeException ex) {
                queue.returnToManual(item.eventId(), ex.getMessage());
                log.warn("order.created DLT replay failed; returned to MANUAL eventId={} orderId={}",
                        item.eventId(), item.orderId(), ex);
            }
        }
    }
}
