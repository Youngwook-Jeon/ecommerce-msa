package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;
import com.project.young.orderservice.application.port.output.OrderPaymentReconciliationFailurePort;
import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus;
import com.project.young.orderservice.dataaccess.repository.OrderPaymentReconciliationFailureJpaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@Transactional
public class OrderPaymentReconciliationFailureAdapter implements OrderPaymentReconciliationFailurePort {

    private final OrderPaymentReconciliationFailureJpaRepository repository;

    public OrderPaymentReconciliationFailureAdapter(OrderPaymentReconciliationFailureJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> findNonRetryingOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findOrderIdsByHandlingStatusNotAndOrderIdIn(
                OrderPaymentReconciliationStatus.RETRYING, orderIds));
    }

    @Override
    public void recordFailure(UUID orderId, String userId, UUID paymentId, String paymentStatus, String errorMessage,
                              Instant occurredAt, int maxAttempts) {
        repository.recordFailure(orderId, userId, paymentId, paymentStatus, truncate(errorMessage), occurredAt,
                Math.max(1, maxAttempts));
    }

    @Override
    public void resolve(UUID orderId) {
        repository.resolve(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPaymentReconciliationEscalationView> findEscalated(int limit) {
        return repository.findByHandlingStatusOrderByLastFailureAtAsc(
                        OrderPaymentReconciliationStatus.ESCALATED,
                        PageRequest.of(0, Math.clamp(limit, 1, 500)))
                .stream()
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderPaymentReconciliationEscalationView> findEscalatedByOrderId(UUID orderId) {
        return repository.findByOrderIdAndHandlingStatus(orderId, OrderPaymentReconciliationStatus.ESCALATED)
                .map(this::toView);
    }

    @Override
    public boolean claimForManualReplay(UUID orderId, Instant occurredAt) {
        return repository.claimForManualReplay(orderId, occurredAt) == 1;
    }

    @Override
    public boolean closeManually(UUID orderId, String reason, Instant occurredAt) {
        return repository.closeManually(orderId, truncate(reason), occurredAt) == 1;
    }

    @Override
    public boolean claimForRefund(UUID orderId, UUID compensationEventId, String reason, Instant occurredAt) {
        return repository.claimForRefund(orderId, compensationEventId, truncate(reason), occurredAt) == 1;
    }

    private OrderPaymentReconciliationEscalationView toView(
            com.project.young.orderservice.dataaccess.entity.OrderPaymentReconciliationFailureEntity item
    ) {
        return new OrderPaymentReconciliationEscalationView(
                item.getOrderId(), item.getUserId(), item.getPaymentId(), item.getPaymentStatus(),
                item.getAttempts(), item.getLastError(), item.getFirstFailureAt(), item.getLastFailureAt(),
                item.getHandlingStatus(), item.getCompensationEventId(), item.getResolutionReason(), item.getResolvedAt());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 4096 ? value : value.substring(0, 4096);
    }
}
