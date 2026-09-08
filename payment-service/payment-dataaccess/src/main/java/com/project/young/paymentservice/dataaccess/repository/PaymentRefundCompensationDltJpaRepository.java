package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.dataaccess.entity.PaymentRefundCompensationDltEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import com.project.young.paymentservice.application.compensation.RefundCompensationDltStatus;

public interface PaymentRefundCompensationDltJpaRepository extends JpaRepository<PaymentRefundCompensationDltEntity, UUID> {

    List<PaymentRefundCompensationDltEntity> findTop100ByHandlingStatusOrderByCreatedAt(RefundCompensationDltStatus status);

    @Modifying
    @Query(value = """
            INSERT INTO payment_refund_compensation_dlts (compensation_event_id, payment_id, order_id, source_topic, dlt_topic, source_partition, source_offset, failure_exception_class, failure_message, created_at)
            VALUES (:eventId, :paymentId, :orderId, :sourceTopic, :dltTopic, :partition, :offset, :exceptionClass, :message, :createdAt)
            ON CONFLICT (compensation_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") UUID eventId, @Param("paymentId") UUID paymentId, @Param("orderId") UUID orderId,
                       @Param("sourceTopic") String sourceTopic, @Param("dltTopic") String dltTopic,
                       @Param("partition") Integer partition, @Param("offset") Long offset,
                       @Param("exceptionClass") String exceptionClass, @Param("message") String message,
                       @Param("createdAt") Instant createdAt);

    @Modifying
    @Query(value = "UPDATE payment_refund_compensation_dlts SET handling_status = 'REPLAYING', replay_started_at = :startedAt, replay_attempts = replay_attempts + 1 WHERE compensation_event_id = :id AND handling_status = 'MANUAL'", nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("startedAt") Instant startedAt);

    @Modifying
    @Query(value = "UPDATE payment_refund_compensation_dlts SET handling_status = 'RESOLVED', replay_started_at = NULL WHERE compensation_event_id = :id", nativeQuery = true)
    int resolve(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE payment_refund_compensation_dlts SET handling_status = 'MANUAL', replay_started_at = NULL, failure_message = :message WHERE compensation_event_id = :id", nativeQuery = true)
    int returnManual(@Param("id") UUID id, @Param("message") String message);
    @Modifying @Query(value = "UPDATE payment_refund_compensation_dlts SET handling_status = 'ESCALATED', replay_started_at = NULL, failure_message = :message WHERE compensation_event_id = :id AND handling_status = 'MANUAL'", nativeQuery = true)
    int escalate(@Param("id") UUID id, @Param("message") String message);

    @Modifying
    @Query(value = "UPDATE payment_refund_compensation_dlts SET handling_status = 'MANUAL', replay_started_at = NULL WHERE handling_status = 'REPLAYING' AND replay_started_at < :threshold", nativeQuery = true)
    int releaseExpired(@Param("threshold") Instant threshold);
}
