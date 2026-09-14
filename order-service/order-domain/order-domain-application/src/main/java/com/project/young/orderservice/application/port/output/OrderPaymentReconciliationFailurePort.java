package com.project.young.orderservice.application.port.output;

import com.project.young.orderservice.application.dto.OrderPaymentReconciliationEscalationView;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OrderPaymentReconciliationFailurePort {

    Set<UUID> findEscalatedOrderIds(Collection<UUID> orderIds);

    void recordFailure(
            UUID orderId,
            String userId,
            UUID paymentId,
            String paymentStatus,
            String errorMessage,
            Instant occurredAt,
            int maxAttempts
    );

    void resolve(UUID orderId);

    List<OrderPaymentReconciliationEscalationView> findEscalated(int limit);
}
