package com.project.young.orderservice.dataaccess.entity;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_requested_outbox")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestedOutboxEntity {

    private static final TimeBasedEpochGenerator UUID_GENERATOR = Generators.timeBasedEpochGenerator();

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;
    @Column(name = "compensation_event_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID compensationEventId;
    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;
    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;
    @Column(name = "user_id", length = 36)
    private String userId;
    @Column(name = "reason", length = 512)
    private String reason;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (id == null) {
            id = UUID_GENERATOR.generate();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
