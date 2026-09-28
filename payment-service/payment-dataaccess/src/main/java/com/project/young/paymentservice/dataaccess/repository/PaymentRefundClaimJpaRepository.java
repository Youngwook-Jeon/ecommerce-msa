package com.project.young.paymentservice.dataaccess.repository;

import java.util.Optional;

import com.project.young.paymentservice.dataaccess.entity.PaymentRefundClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentRefundClaimJpaRepository extends JpaRepository<PaymentRefundClaimEntity, UUID> {

    Optional<PaymentRefundClaimEntity> findByProviderRefundId(String providerRefundId);

    @Query(value = """
            SELECT c.* FROM payment_refund_claims c JOIN payments p ON p.id = c.payment_id
            WHERE c.request_kind = 'CUSTOMER' AND c.provider_refund_state = 'SUCCEEDED' AND p.provider = 'STRIPE'
                AND c.provider_refund_id IS NOT NULL AND c.provider_refund_succeeded_at >= :succeededSince
                AND c.provider_refund_checked_at < :checkedBefore
            ORDER BY c.provider_refund_checked_at, c.payment_id
            """, nativeQuery = true)
    List<PaymentRefundClaimEntity> findRecentCustomerSuccesses(@Param("succeededSince") Instant succeededSince,
            @Param("checkedBefore") Instant checkedBefore, org.springframework.data.domain.Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE payment_refund_claims
            SET provider_refund_id = :providerRefundId, provider_refund_state = :state,
                provider_refund_checked_at = :now,
                provider_refund_succeeded_at = CASE WHEN :state = 'SUCCEEDED'
                    THEN COALESCE(provider_refund_succeeded_at, :now) ELSE provider_refund_succeeded_at END,
                provider_refund_failed_at = CASE WHEN :state = 'FAILED'
                    THEN COALESCE(provider_refund_failed_at, :now) ELSE provider_refund_failed_at END
            WHERE payment_id = :paymentId AND request_id = :requestId AND request_kind = 'CUSTOMER'
              AND (provider_refund_id IS NULL OR provider_refund_id = :providerRefundId)
              AND (provider_refund_state IS DISTINCT FROM 'FAILED' OR :state = 'FAILED')
              AND (:state <> 'PENDING' OR provider_refund_state IS NULL OR provider_refund_state = 'PENDING')
            """, nativeQuery = true)
    int recordCustomerObservation(@Param("paymentId") UUID paymentId, @Param("requestId") UUID requestId,
                                  @Param("providerRefundId") String providerRefundId,
                                  @Param("state") String state, @Param("now") Instant now);

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

    @Modifying
    @Query(value = """
            UPDATE payment_refund_claims
            SET provider_refund_id = :providerRefundId, provider_refund_state = :state,
                provider_refund_checked_at = :now
            WHERE payment_id = :paymentId AND request_id = :requestId AND request_kind = :kind
              AND (provider_refund_id IS NULL OR provider_refund_id = :providerRefundId)
              AND (:state <> 'PENDING' OR provider_refund_state IS NULL OR provider_refund_state = 'PENDING')
            """, nativeQuery = true)
    int recordProviderResult(@Param("paymentId") UUID paymentId, @Param("requestId") UUID requestId,
                             @Param("kind") String kind, @Param("providerRefundId") String providerRefundId,
                             @Param("state") String state, @Param("now") Instant now);

    @Query(value = """
            SELECT c.* FROM payment_refund_claims c
            WHERE c.first_attempt_at IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM customer_refund_reviews r
                    WHERE c.request_kind = 'CUSTOMER' AND r.refund_id = c.request_id
                      AND r.handling_status = 'ESCALATED')
              AND (c.provider_refund_state IS NULL OR c.provider_refund_state IN ('PENDING', 'SUCCEEDED', 'FAILED'))
              AND ((c.request_kind = 'CUSTOMER' AND c.provider_refund_state = 'FAILED' AND NOT EXISTS (
                    SELECT 1 FROM payment_outbox o WHERE o.refund_id = c.request_id
                      AND o.event_type = 'CUSTOMER_REFUND_FAILED'))
                OR (c.request_kind = 'CUSTOMER' AND c.provider_refund_state IS DISTINCT FROM 'FAILED' AND NOT EXISTS (
                    SELECT 1 FROM customer_refund_processings p WHERE p.refund_id = c.request_id))
                OR (c.request_kind = 'COMPENSATION' AND c.provider_refund_state = 'FAILED' AND NOT EXISTS (
                    SELECT 1 FROM payment_refund_compensation_dlts d WHERE d.compensation_event_id = c.request_id))
                OR (c.request_kind = 'COMPENSATION' AND c.provider_refund_state IS DISTINCT FROM 'FAILED' AND NOT EXISTS (
                    SELECT 1 FROM payment_refund_compensations p WHERE p.compensation_event_id = c.request_id)))
            ORDER BY COALESCE(c.provider_refund_checked_at, c.first_attempt_at), c.payment_id
            """, nativeQuery = true)
    List<PaymentRefundClaimEntity> findUnfinalized(org.springframework.data.domain.Pageable pageable);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM customer_refund_reviews
                WHERE payment_id = :paymentId AND refund_id = :requestId AND handling_status = 'ESCALATED')
            """, nativeQuery = true)
    boolean isCustomerReviewEscalated(@Param("paymentId") UUID paymentId, @Param("requestId") UUID requestId);
}
