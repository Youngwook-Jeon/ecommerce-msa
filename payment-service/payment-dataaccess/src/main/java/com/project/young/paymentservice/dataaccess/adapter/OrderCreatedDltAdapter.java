package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.application.dto.OrderCreatedDltView;
import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import com.project.young.paymentservice.dataaccess.repository.PaymentOrderCreatedDltJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class OrderCreatedDltAdapter implements OrderCreatedDltPort {

    private final PaymentOrderCreatedDltJpaRepository repository;

    public OrderCreatedDltAdapter(PaymentOrderCreatedDltJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean recordIfAbsent(RecordOrderCreatedDltCommand command) {
        return repository.insertIfAbsent(command.eventId(), command.orderId(), command.userId(), command.totalAmount(),
                command.currency(), command.sourceTopic(), command.dltTopic(), command.sourcePartition(),
                command.sourceOffset(), truncate(command.failureExceptionClass(), 1024),
                truncate(command.failureMessage(), 4096), Instant.now()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderCreatedDltView> findManual(int limit) {
        return repository.findTop100ByHandlingStatusOrderByCreatedAt(OrderCreatedDltStatus.MANUAL).stream()
                .limit(limit)
                .map(item -> new OrderCreatedDltView(item.getEventId(), item.getOrderId(), item.getUserId(),
                        item.getTotalAmount(), item.getCurrency(), item.getCreatedAt(), item.getReplayAttempts()))
                .toList();
    }

    @Override
    public boolean claimForReplay(UUID eventId, Instant startedAt) {
        return repository.claim(eventId, startedAt) == 1;
    }

    @Override
    public void resolve(UUID eventId) {
        repository.resolve(eventId);
    }

    @Override
    public void returnToManual(UUID eventId, String failureMessage) {
        repository.returnManual(eventId, truncate(failureMessage, 4096));
    }

    @Override
    public void escalate(UUID eventId, String failureMessage) {
        repository.escalate(eventId, truncate(failureMessage, 4096));
    }

    @Override
    public int returnExpiredReplaysToManual(Instant threshold) {
        return repository.releaseExpired(threshold);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
