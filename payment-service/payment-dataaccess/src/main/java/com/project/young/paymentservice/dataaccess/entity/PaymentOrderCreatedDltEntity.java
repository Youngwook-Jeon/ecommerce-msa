package com.project.young.paymentservice.dataaccess.entity;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_order_created_dlts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderCreatedDltEntity {

    @Id
    @Column(name = "event_id", columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "total_amount", nullable = false, length = 32)
    private String totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "source_topic", nullable = false)
    private String sourceTopic;

    @Column(name = "dlt_topic", nullable = false)
    private String dltTopic;

    @Column(name = "source_partition")
    private Integer sourcePartition;

    @Column(name = "source_offset")
    private Long sourceOffset;

    @Column(name = "failure_exception_class", length = 1024)
    private String failureExceptionClass;

    @Column(name = "failure_message", length = 4096)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "handling_status", nullable = false)
    private OrderCreatedDltStatus handlingStatus;

    @Column(name = "replay_started_at")
    private Instant replayStartedAt;

    @Column(name = "replay_attempts", nullable = false)
    private int replayAttempts;
}
