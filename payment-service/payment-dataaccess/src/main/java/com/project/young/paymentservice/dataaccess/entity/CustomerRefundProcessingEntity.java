package com.project.young.paymentservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_refund_processings")
@Getter
public class CustomerRefundProcessingEntity {

    @Id
    @Column(name = "refund_id", nullable = false, columnDefinition = "UUID")
    private UUID refundId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected CustomerRefundProcessingEntity() {
    }
}
