package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.PaymentOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PaymentOutboxJpaRepository extends JpaRepository<PaymentOutboxEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO payment_outbox (event_id, refund_id, payment_id, order_id, user_id,
                    event_type, amount, failure_reason, occurred_at)
            VALUES (:eventId, :refundId, :paymentId, :orderId, :userId,
                    'CUSTOMER_REFUND_FAILED', 0, :reason, :occurredAt)
            ON CONFLICT (refund_id, event_type) WHERE event_type = 'CUSTOMER_REFUND_FAILED' DO NOTHING
            """, nativeQuery = true)
    int insertCustomerRefundFailed(@Param("eventId") UUID eventId, @Param("refundId") UUID refundId,
                                   @Param("paymentId") UUID paymentId, @Param("orderId") UUID orderId,
                                   @Param("userId") String userId, @Param("reason") String reason,
                                   @Param("occurredAt") Instant occurredAt);
}
