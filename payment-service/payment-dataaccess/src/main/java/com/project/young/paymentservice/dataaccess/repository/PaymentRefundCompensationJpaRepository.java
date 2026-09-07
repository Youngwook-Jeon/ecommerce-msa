package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.PaymentRefundCompensationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PaymentRefundCompensationJpaRepository
        extends JpaRepository<PaymentRefundCompensationEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO payment_refund_compensations (
                compensation_event_id, payment_id, order_id, processed_at
            ) VALUES (:compensationEventId, :paymentId, :orderId, :processedAt)
            ON CONFLICT (compensation_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("compensationEventId") UUID compensationEventId,
            @Param("paymentId") UUID paymentId,
            @Param("orderId") UUID orderId,
            @Param("processedAt") Instant processedAt
    );
}
