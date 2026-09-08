package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.port.output.RefundCompensationDltPort;
import com.project.young.paymentservice.dataaccess.repository.PaymentRefundCompensationDltJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.project.young.paymentservice.application.dto.RefundCompensationDltView;
import com.project.young.paymentservice.application.compensation.RefundCompensationDltStatus;

@Repository
@Transactional
public class RefundCompensationDltAdapter implements RefundCompensationDltPort {

    private final PaymentRefundCompensationDltJpaRepository repository;

    public RefundCompensationDltAdapter(PaymentRefundCompensationDltJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean recordIfAbsent(RecordRefundCompensationDltCommand command) {
        return repository.insertIfAbsent(command.compensationEventId(), command.paymentId(), command.orderId(),
                command.sourceTopic(), command.dltTopic(), command.sourcePartition(), command.sourceOffset(),
                truncate(command.failureExceptionClass(), 1024), truncate(command.failureMessage(), 4096), Instant.now()) == 1;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundCompensationDltView> findManual(int limit) {
        return repository.findTop100ByHandlingStatusOrderByCreatedAt(RefundCompensationDltStatus.MANUAL).stream().limit(limit)
                .map(e -> new RefundCompensationDltView(e.getCompensationEventId(), e.getPaymentId(), e.getOrderId(), e.getCreatedAt(), e.getReplayAttempts())).toList();
    }

    @Override
    public boolean claimForReplay(UUID id, Instant startedAt) {
        return repository.claim(id, startedAt) == 1;
    }

    @Override
    public void resolve(UUID id) {
        repository.resolve(id);
    }

    @Override
    public void returnToManual(UUID id, String message) {
        repository.returnManual(id, truncate(message, 4096));
    }

    @Override
    public void escalate(UUID id, String message) {
        repository.escalate(id, truncate(message, 4096));
    }

    @Override
    public int returnExpiredReplaysToManual(Instant threshold) {
        return repository.releaseExpired(threshold);
    }
}
