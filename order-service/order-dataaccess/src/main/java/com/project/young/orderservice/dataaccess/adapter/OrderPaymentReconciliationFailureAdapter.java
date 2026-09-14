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
    public Set<UUID> findEscalatedOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(repository.findOrderIdsByHandlingStatusAndOrderIdIn(
                OrderPaymentReconciliationStatus.ESCALATED, orderIds));
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
                .map(item -> new OrderPaymentReconciliationEscalationView(
                        item.getOrderId(), item.getUserId(), item.getPaymentId(), item.getPaymentStatus(),
                        item.getAttempts(), item.getLastError(), item.getFirstFailureAt(), item.getLastFailureAt()))
                .toList();
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 4096 ? value : value.substring(0, 4096);
    }
}
