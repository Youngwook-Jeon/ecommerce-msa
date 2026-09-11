package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.application.provider.ProviderWebhookInboxStatus;
import com.project.young.paymentservice.dataaccess.entity.ProviderWebhookInboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ProviderWebhookInboxJpaRepository extends JpaRepository<ProviderWebhookInboxEntity, String> {

    List<ProviderWebhookInboxEntity> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByReceivedAt(
            Collection<ProviderWebhookInboxStatus> statuses,
            Instant now
    );

    List<ProviderWebhookInboxEntity> findTop100ByStatusOrderByUpdatedAt(ProviderWebhookInboxStatus status);

    @Modifying
    @Query(value = "INSERT INTO provider_webhook_inbox (event_id, provider, provider_payment_id, success, outcome, failure_reason, status, attempts, next_retry_at, received_at, updated_at) "
            + "VALUES (:eventId, :provider, :providerPaymentId, :success, :outcome, :failureReason, :status, 0, :now, :now, :now) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("provider") String provider,
            @Param("providerPaymentId") String providerPaymentId,
            @Param("success") boolean success,
            @Param("outcome") String outcome,
            @Param("failureReason") String failureReason,
            @Param("status") String status,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = "UPDATE provider_webhook_inbox SET status=:processingStatus, processing_started_at=:now, attempts=attempts+1, updated_at=:now "
            + "WHERE event_id=:eventId AND status IN (:receivedStatus, :waitingStatus)", nativeQuery = true)
    int claim(
            @Param("eventId") String eventId,
            @Param("receivedStatus") String receivedStatus,
            @Param("waitingStatus") String waitingStatus,
            @Param("processingStatus") String processingStatus,
            @Param("now") Instant now
    );

    @Modifying
    @Query(value = "UPDATE provider_webhook_inbox SET status=:status, processing_started_at=NULL, last_failure_message=NULL, updated_at=:now "
            + "WHERE event_id=:eventId AND status=:processingStatus", nativeQuery = true)
    int markApplied(@Param("eventId") String eventId, @Param("processingStatus") String processingStatus,
                    @Param("status") String status, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_webhook_inbox SET status=:status, processing_started_at=NULL, last_failure_message=:message, next_retry_at=:nextRetryAt, updated_at=:now "
            + "WHERE event_id=:eventId AND status=:processingStatus", nativeQuery = true)
    int returnToWaiting(@Param("eventId") String eventId, @Param("processingStatus") String processingStatus,
                        @Param("status") String status, @Param("message") String message,
                        @Param("nextRetryAt") Instant nextRetryAt, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_webhook_inbox SET status=:status, processing_started_at=NULL, last_failure_message=:message, updated_at=:now "
            + "WHERE event_id=:eventId AND status=:processingStatus", nativeQuery = true)
    int escalate(@Param("eventId") String eventId, @Param("processingStatus") String processingStatus,
                 @Param("status") String status, @Param("message") String message, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE provider_webhook_inbox SET status=:waitingStatus, processing_started_at=NULL, updated_at=:now "
            + "WHERE status=:processingStatus AND processing_started_at < :threshold", nativeQuery = true)
    int releaseExpired(
            @Param("processingStatus") String processingStatus,
            @Param("waitingStatus") String waitingStatus,
            @Param("threshold") Instant threshold,
            @Param("now") Instant now
    );
}
