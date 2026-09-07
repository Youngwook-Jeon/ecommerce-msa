package com.project.young.paymentservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refund_compensations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundCompensationEntity {

    @Id
    @Column(name = "compensation_event_id", nullable = false, columnDefinition = "UUID")
    private UUID compensationEventId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
