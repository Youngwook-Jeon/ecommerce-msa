package com.project.young.paymentservice.dataaccess.repository;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.dataaccess.entity.PaymentOrderCreatedDltEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentOrderCreatedDltJpaRepository extends JpaRepository<PaymentOrderCreatedDltEntity, UUID> {

    List<PaymentOrderCreatedDltEntity> findTop100ByHandlingStatusOrderByCreatedAt(OrderCreatedDltStatus status);

    @Modifying
    @Query(value = """
            INSERT INTO payment_order_created_dlts (event_id, order_id, user_id, total_amount, currency, source_topic, dlt_topic, source_partition, source_offset, failure_exception_class, failure_message, created_at)
            VALUES (:eventId, :orderId, :userId, :totalAmount, :currency, :sourceTopic, :dltTopic, :partition, :offset, :exceptionClass, :message, :createdAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("orderId") UUID orderId,
            @Param("userId") String userId,
            @Param("totalAmount") String totalAmount,
            @Param("currency") String currency,
            @Param("sourceTopic") String sourceTopic,
            @Param("dltTopic") String dltTopic,
            @Param("partition") Integer partition,
            @Param("offset") Long offset,
            @Param("exceptionClass") String exceptionClass,
            @Param("message") String message,
            @Param("createdAt") Instant createdAt
    );

    @Modifying
    @Query(value = "UPDATE payment_order_created_dlts SET handling_status='REPLAYING', replay_started_at=:startedAt, replay_attempts=replay_attempts+1 WHERE event_id=:eventId AND handling_status='MANUAL'", nativeQuery = true)
    int claim(@Param("eventId") UUID eventId, @Param("startedAt") Instant startedAt);

    @Modifying
    @Query(value = "UPDATE payment_order_created_dlts SET handling_status='RESOLVED', replay_started_at=NULL WHERE event_id=:eventId", nativeQuery = true)
    int resolve(@Param("eventId") UUID eventId);

    @Modifying
    @Query(value = "UPDATE payment_order_created_dlts SET handling_status='MANUAL', replay_started_at=NULL, failure_message=:message WHERE event_id=:eventId", nativeQuery = true)
    int returnManual(@Param("eventId") UUID eventId, @Param("message") String message);

    @Modifying
    @Query(value = "UPDATE payment_order_created_dlts SET handling_status='ESCALATED', replay_started_at=NULL, failure_message=:message WHERE event_id=:eventId AND handling_status='MANUAL'", nativeQuery = true)
    int escalate(@Param("eventId") UUID eventId, @Param("message") String message);

    @Modifying
    @Query(value = "UPDATE payment_order_created_dlts SET handling_status='MANUAL', replay_started_at=NULL WHERE handling_status='REPLAYING' AND replay_started_at < :threshold", nativeQuery = true)
    int releaseExpired(@Param("threshold") Instant threshold);
}
