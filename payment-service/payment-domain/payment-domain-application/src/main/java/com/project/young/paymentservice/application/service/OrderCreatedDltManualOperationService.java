package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.exception.OrderCreatedDltNotFoundException;
import com.project.young.paymentservice.application.exception.OrderCreatedDltStateConflictException;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

@Service
public class OrderCreatedDltManualOperationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedDltManualOperationService.class);

    private final OrderCreatedDltPort queue;
    private final PaymentApplicationService payments;
    private final Clock clock;

    public OrderCreatedDltManualOperationService(
            OrderCreatedDltPort queue,
            PaymentApplicationService payments,
            Clock clock
    ) {
        this.queue = queue;
        this.payments = payments;
        this.clock = clock;
    }

    public void replay(UUID eventId) {
        var item = queue.findByEventId(eventId).orElseThrow(() -> new OrderCreatedDltNotFoundException(eventId));
        if (!queue.claimForManualReplay(eventId, clock.instant())) {
            throw new OrderCreatedDltStateConflictException(eventId);
        }

        try {
            payments.processPayment(ProcessPaymentCommand.fromOrderCreated(
                    item.orderId(), item.userId(), item.totalAmount(), item.currency()));
            queue.resolve(eventId);
            log.info("Manually resolved order.created DLT replay eventId={} orderId={}", eventId, item.orderId());
        } catch (RuntimeException ex) {
            queue.returnToManual(eventId, ex.getMessage());
            log.warn("Manual order.created DLT replay failed; returned to MANUAL eventId={} orderId={}",
                    eventId, item.orderId(), ex);
            throw ex;
        }
    }

    public void resolve(UUID eventId, String reason) {
        if (queue.findByEventId(eventId).isEmpty()) {
            throw new OrderCreatedDltNotFoundException(eventId);
        }
        if (!queue.resolveManually(eventId, reason)) {
            throw new OrderCreatedDltStateConflictException(eventId);
        }
        log.info("Manually resolved order.created DLT eventId={} reason={}", eventId, reason);
    }
}
