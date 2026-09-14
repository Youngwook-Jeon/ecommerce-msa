package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class OrderCreatedDltApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedDltApplicationService.class);

    private final OrderCreatedDltPort queue;

    public OrderCreatedDltApplicationService(OrderCreatedDltPort queue) {
        this.queue = queue;
    }

    @Transactional
    public boolean recordManualFollowUp(RecordOrderCreatedDltCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.eventId(), "eventId must not be null");
        Objects.requireNonNull(command.orderId(), "orderId must not be null");
        boolean created = queue.recordIfAbsent(command);
        log.warn("Recorded order.created DLT manual follow-up eventId={} orderId={} newlyCreated={} cause={}",
                command.eventId(), command.orderId(), created, command.failureExceptionClass());
        return created;
    }
}
