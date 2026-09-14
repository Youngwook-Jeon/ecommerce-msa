package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.application.dto.OrderCreatedDltView;
import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;
import com.project.young.paymentservice.application.dto.query.OrderCreatedDltOperationsView;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import com.project.young.paymentservice.dataaccess.entity.PaymentOrderCreatedDltEntity;
import com.project.young.paymentservice.dataaccess.repository.PaymentOrderCreatedDltJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        return repository.findByHandlingStatusOrderByCreatedAtDesc(OrderCreatedDltStatus.MANUAL, page(limit)).stream()
                .map(item -> new OrderCreatedDltView(item.getEventId(), item.getOrderId(), item.getUserId(),
                        item.getTotalAmount(), item.getCurrency(), item.getCreatedAt(), item.getReplayAttempts()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderCreatedDltOperationsView> findForOperations(OrderCreatedDltStatus status, int limit) {
        List<PaymentOrderCreatedDltEntity> items = status == null
                ? repository.findAllByOrderByCreatedAtDesc(page(limit))
                : repository.findByHandlingStatusOrderByCreatedAtDesc(status, page(limit));
        return items.stream().map(this::toOperationsView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderCreatedDltOperationsView> findByEventId(UUID eventId) {
        return repository.findById(eventId).map(this::toOperationsView);
    }

    @Override
    public boolean claimForReplay(UUID eventId, Instant startedAt) {
        return repository.claim(eventId, startedAt) == 1;
    }

    @Override
    public boolean claimForManualReplay(UUID eventId, Instant startedAt) {
        return repository.claimForManualReplay(eventId, startedAt) == 1;
    }

    @Override
    public void resolve(UUID eventId) {
        repository.resolve(eventId);
    }

    @Override
    public boolean resolveManually(UUID eventId, String reason) {
        return repository.resolveManually(eventId, truncate("Manually resolved: " + reason, 4096)) == 1;
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

    private static PageRequest page(int limit) {
        return PageRequest.of(0, Math.clamp(limit, 1, 500));
    }

    private OrderCreatedDltOperationsView toOperationsView(PaymentOrderCreatedDltEntity item) {
        return new OrderCreatedDltOperationsView(
                item.getEventId(), item.getOrderId(), item.getUserId(), item.getTotalAmount(), item.getCurrency(),
                item.getHandlingStatus(), item.getReplayAttempts(), item.getFailureExceptionClass(),
                item.getFailureMessage(), item.getCreatedAt(), item.getReplayStartedAt()
        );
    }
}
