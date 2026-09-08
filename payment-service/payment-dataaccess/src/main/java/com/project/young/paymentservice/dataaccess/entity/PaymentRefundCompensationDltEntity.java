package com.project.young.paymentservice.dataaccess.entity;

import jakarta.persistence.*;
import com.project.young.paymentservice.application.compensation.RefundCompensationDltStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refund_compensation_dlts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundCompensationDltEntity {

    @Id
    @Column(name = "compensation_event_id", columnDefinition = "UUID")
    private UUID compensationEventId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

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
    private RefundCompensationDltStatus handlingStatus;

    @Column(name = "replay_started_at")
    private Instant replayStartedAt;

    @Column(name = "replay_attempts", nullable = false)
    private int replayAttempts;
}
