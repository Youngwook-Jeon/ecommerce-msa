package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.OrderCreatedDltView;
import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;
import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.application.dto.query.OrderCreatedDltOperationsView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderCreatedDltPort {

    boolean recordIfAbsent(RecordOrderCreatedDltCommand command);

    List<OrderCreatedDltView> findManual(int limit);

    List<OrderCreatedDltOperationsView> findForOperations(OrderCreatedDltStatus status, int limit);

    Optional<OrderCreatedDltOperationsView> findByEventId(UUID eventId);

    boolean claimForReplay(UUID eventId, Instant startedAt);

    boolean claimForManualReplay(UUID eventId, Instant startedAt);

    void resolve(UUID eventId);

    boolean resolveManually(UUID eventId, String reason);

    void returnToManual(UUID eventId, String failureMessage);

    void escalate(UUID eventId, String failureMessage);

    int returnExpiredReplaysToManual(Instant threshold);
}
