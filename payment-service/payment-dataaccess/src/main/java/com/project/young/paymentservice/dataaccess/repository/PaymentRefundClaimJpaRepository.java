package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.PaymentRefundClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface PaymentRefundClaimJpaRepository extends JpaRepository<PaymentRefundClaimEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO payment_refund_claims (payment_id, request_id, request_kind)
            VALUES (:paymentId, :requestId, :requestKind)
            ON CONFLICT (payment_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("paymentId") UUID paymentId,
                       @Param("requestId") UUID requestId,
                       @Param("requestKind") String requestKind);

    @Modifying
    @Query(value = """
            UPDATE payment_refund_claims SET first_attempt_at = :now
            WHERE payment_id = :paymentId AND first_attempt_at IS NULL
            """, nativeQuery = true)
    int markAttemptStarted(@Param("paymentId") UUID paymentId, @Param("now") Instant now);
}
