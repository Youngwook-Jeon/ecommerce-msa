package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.CustomerRefundProcessingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CustomerRefundProcessingJpaRepository extends JpaRepository<CustomerRefundProcessingEntity, UUID> {

    @Query(value = "SELECT EXISTS (SELECT 1 FROM customer_refund_processings WHERE refund_id = :refundId)", nativeQuery = true)
    boolean existsByRefundId(@Param("refundId") UUID refundId);

    @Modifying
    @Query(value = "INSERT INTO customer_refund_processings (refund_id, payment_id, order_id, user_id, processed_at) VALUES (:refundId,:paymentId,:orderId,:userId,:processedAt) ON CONFLICT (refund_id) DO NOTHING", nativeQuery = true)
    int insert(@Param("refundId") UUID refundId, @Param("paymentId") UUID paymentId, @Param("orderId") UUID orderId,
               @Param("userId") String userId, @Param("processedAt") Instant processedAt);
}
