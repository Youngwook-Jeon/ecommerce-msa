package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.OrderCreatedDltView;
import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderCreatedDltPort {

    boolean recordIfAbsent(RecordOrderCreatedDltCommand command);

    List<OrderCreatedDltView> findManual(int limit);

    boolean claimForReplay(UUID eventId, Instant startedAt);

    void resolve(UUID eventId);

    void returnToManual(UUID eventId, String failureMessage);

    void escalate(UUID eventId, String failureMessage);

    int returnExpiredReplaysToManual(Instant threshold);
}
