package com.project.young.orderservice.dataaccess.repository;

import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus;
import com.project.young.orderservice.dataaccess.entity.OrderPaymentReconciliationFailureEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderPaymentReconciliationFailureJpaRepository
        extends JpaRepository<OrderPaymentReconciliationFailureEntity, UUID> {

    @Query("select f.orderId from OrderPaymentReconciliationFailureEntity f where f.handlingStatus = :status and f.orderId in :orderIds")
    List<UUID> findOrderIdsByHandlingStatusAndOrderIdIn(
            @Param("status") OrderPaymentReconciliationStatus status,
            @Param("orderIds") Collection<UUID> orderIds
    );

    List<OrderPaymentReconciliationFailureEntity> findByHandlingStatusOrderByLastFailureAtAsc(
            OrderPaymentReconciliationStatus status,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            INSERT INTO order_payment_reconciliation_failures
                (order_id, user_id, payment_id, payment_status, attempts, handling_status, last_error, first_failure_at, last_failure_at)
            VALUES
                (:orderId, :userId, :paymentId, :paymentStatus, 1,
                 CASE WHEN 1 >= :maxAttempts THEN 'ESCALATED' ELSE 'RETRYING' END,
                 :errorMessage, :occurredAt, :occurredAt)
            ON CONFLICT (order_id) DO UPDATE SET
                user_id = EXCLUDED.user_id,
                payment_id = EXCLUDED.payment_id,
                payment_status = EXCLUDED.payment_status,
                attempts = order_payment_reconciliation_failures.attempts + 1,
                handling_status = CASE
                    WHEN order_payment_reconciliation_failures.handling_status = 'ESCALATED' THEN 'ESCALATED'
                    WHEN order_payment_reconciliation_failures.attempts + 1 >= :maxAttempts THEN 'ESCALATED'
                    ELSE 'RETRYING'
                END,
                last_error = EXCLUDED.last_error,
                last_failure_at = EXCLUDED.last_failure_at
            """, nativeQuery = true)
    int recordFailure(
            @Param("orderId") UUID orderId,
            @Param("userId") String userId,
            @Param("paymentId") UUID paymentId,
            @Param("paymentStatus") String paymentStatus,
            @Param("errorMessage") String errorMessage,
            @Param("occurredAt") Instant occurredAt,
            @Param("maxAttempts") int maxAttempts
    );

    @Modifying
    @Query("""
            update OrderPaymentReconciliationFailureEntity f
               set f.handlingStatus = com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus.RESOLVED
             where f.orderId = :orderId
               and f.handlingStatus = com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus.RETRYING
            """)
    int resolve(@Param("orderId") UUID orderId);
}
