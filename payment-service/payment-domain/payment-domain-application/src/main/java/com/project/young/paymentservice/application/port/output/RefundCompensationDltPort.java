package com.project.young.paymentservice.application.port.output;

import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.dto.RefundCompensationDltView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RefundCompensationDltPort {
    boolean recordIfAbsent(RecordRefundCompensationDltCommand command);
    List<RefundCompensationDltView> findManual(int limit);
    boolean claimForReplay(UUID compensationEventId, Instant startedAt);
    void resolve(UUID compensationEventId);
    void returnToManual(UUID compensationEventId, String failureMessage);
    void escalate(UUID compensationEventId, String failureMessage);
    int returnExpiredReplaysToManual(Instant threshold);
}
