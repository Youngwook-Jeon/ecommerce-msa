package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.CustomerRefundReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CustomerRefundReviewJpaRepository extends JpaRepository<CustomerRefundReviewEntity, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO customer_refund_reviews (refund_id, payment_id, provider_refund_id,
                handling_status, review_reason, failure_exception_class, failure_message, created_at,
                confirmed_failure_reason, confirmed_failed_at)
            VALUES (:refundId, :paymentId, :providerRefundId, 'ESCALATED', 'CONFIRMED_LATE_FAILURE',
                'ProviderRefundFailed', :reason, CURRENT_TIMESTAMP, :reason, CURRENT_TIMESTAMP)
            ON CONFLICT (refund_id) DO UPDATE SET review_reason = 'CONFIRMED_LATE_FAILURE',
                provider_refund_id = EXCLUDED.provider_refund_id,
                confirmed_failure_reason = EXCLUDED.confirmed_failure_reason,
                confirmed_failed_at = COALESCE(customer_refund_reviews.confirmed_failed_at, EXCLUDED.confirmed_failed_at)
            """, nativeQuery = true)
    int recordConfirmedLateFailure(@Param("refundId") UUID refundId, @Param("paymentId") UUID paymentId,
                                   @Param("providerRefundId") String providerRefundId, @Param("reason") String reason);

    @Modifying
    @Query(value = """
            INSERT INTO customer_refund_reviews (refund_id, payment_id, provider_refund_id,
                    handling_status, failure_exception_class, failure_message, created_at)
            VALUES (:refundId, :paymentId, :providerRefundId, 'ESCALATED', :exceptionClass, :message, :now)
            ON CONFLICT (refund_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("refundId") UUID refundId, @Param("paymentId") UUID paymentId,
                       @Param("providerRefundId") String providerRefundId,
                       @Param("exceptionClass") String exceptionClass, @Param("message") String message,
                       @Param("now") Instant now);
}
