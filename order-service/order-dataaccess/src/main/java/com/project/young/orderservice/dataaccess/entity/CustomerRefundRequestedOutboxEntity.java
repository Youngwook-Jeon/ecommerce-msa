package com.project.young.orderservice.dataaccess.entity;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_refund_requested_outbox")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRefundRequestedOutboxEntity {

    private static final TimeBasedEpochGenerator UUID_GENERATOR = Generators.timeBasedEpochGenerator();

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "UUID")
    private UUID id;

    @Column(name = "refund_id", nullable = false, unique = true, columnDefinition = "UUID")
    private UUID refundId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
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
