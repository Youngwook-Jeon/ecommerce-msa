package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.ProviderRefundWebhookInboxEntity;
import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface ProviderRefundWebhookInboxJpaRepository extends JpaRepository<ProviderRefundWebhookInboxEntity, String> {
    @Modifying
    @Query(value = """
            INSERT INTO provider_refund_webhook_inbox
                (event_id, provider_refund_id, provider_payment_id, refund_state, failure_reason)
            VALUES (:#{#c.eventId()}, :#{#c.providerRefundId()}, :#{#c.providerPaymentId()},
                :#{#c.state().name()}, :reason)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("c") ObserveProviderRefundCommand command, @Param("reason") String reason);

    @Query(value = """
            SELECT * FROM provider_refund_webhook_inbox
            WHERE (status = 'WAITING' AND next_attempt_at <= :now)
                OR (status = 'PROCESSING' AND claimed_at < :expiredBefore)
            ORDER BY next_attempt_at, event_id
            """, nativeQuery = true)
    List<ProviderRefundWebhookInboxEntity> findReady(@Param("now") Instant now,
            @Param("expiredBefore") Instant expiredBefore, Pageable pageable);

    @Modifying
    @Query(value = """
            UPDATE provider_refund_webhook_inbox SET status = 'PROCESSING', claimed_at = :now, attempts = attempts + 1
            WHERE event_id = :eventId AND ((status = 'WAITING' AND next_attempt_at <= :now)
                OR (status = 'PROCESSING' AND claimed_at < :expiredBefore))
            """, nativeQuery = true)
    int claim(@Param("eventId") String eventId, @Param("now") Instant now, @Param("expiredBefore") Instant expiredBefore);

    @Modifying
    @Query(value = """
            UPDATE provider_refund_webhook_inbox SET status = 'APPLIED', last_error = NULL
            WHERE event_id = :eventId AND status = 'PROCESSING' AND claimed_at = :claimedAt
            """, nativeQuery = true)
    int markApplied(@Param("eventId") String eventId, @Param("claimedAt") Instant claimedAt);

    @Modifying
    @Query(value = """
            UPDATE provider_refund_webhook_inbox
            SET status = CASE WHEN attempts >= :maxAttempts THEN 'ESCALATED' ELSE 'WAITING' END,
                next_attempt_at = :nextAttemptAt, last_error = :error
            WHERE event_id = :eventId AND status = 'PROCESSING' AND claimed_at = :claimedAt
            """, nativeQuery = true)
    int retryOrEscalate(@Param("eventId") String eventId, @Param("claimedAt") Instant claimedAt,
                       @Param("maxAttempts") int maxAttempts, @Param("nextAttemptAt") Instant nextAttemptAt,
                       @Param("error") String error);
}
