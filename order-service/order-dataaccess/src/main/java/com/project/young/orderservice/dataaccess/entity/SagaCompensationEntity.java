package com.project.young.orderservice.dataaccess.entity;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_compensation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SagaCompensationEntity {

    private static final TimeBasedEpochGenerator UUID_GENERATOR = Generators.timeBasedEpochGenerator();

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID eventId;

    @Column(name = "payment_id", columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "amount", length = 32)
    private String amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "source_topic", nullable = false, length = 128)
    private String sourceTopic;

    @Column(name = "dlt_topic", nullable = false, length = 128)
    private String dltTopic;

    @Column(name = "source_partition")
    private Integer sourcePartition;

    @Column(name = "source_offset")
    private Long sourceOffset;

    @Column(name = "failure_exception_class", length = 512)
    private String failureExceptionClass;

    @Column(name = "failure_message", length = 2000)
    private String failureMessage;

    @Column(name = "recommended_action", nullable = false, length = 32)
    private String recommendedAction;

    @Column(name = "refund_sla", nullable = false, length = 32)
    private String refundSla;

    @Column(name = "classification_reason", nullable = false, length = 512)
    private String classificationReason;

    @Column(name = "handling_status", nullable = false, length = 32)
    private String handlingStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        if (this.id == null) {
            this.id = UUID_GENERATOR.generate();
        }
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
