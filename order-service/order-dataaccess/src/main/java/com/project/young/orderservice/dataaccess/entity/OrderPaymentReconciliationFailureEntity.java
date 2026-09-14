package com.project.young.orderservice.dataaccess.entity;

import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_payment_reconciliation_failures")
@Getter
public class OrderPaymentReconciliationFailureEntity {

    @Id
    @Column(name = "order_id", columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "payment_status", nullable = false, length = 16)
    private String paymentStatus;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "handling_status", nullable = false, length = 16)
    private OrderPaymentReconciliationStatus handlingStatus;

    @Column(name = "last_error", length = 4096)
    private String lastError;

    @Column(name = "compensation_event_id", columnDefinition = "UUID")
    private UUID compensationEventId;

    @Column(name = "resolution_reason", length = 512)
    private String resolutionReason;

    @Column(name = "first_failure_at", nullable = false)
    private Instant firstFailureAt;

    @Column(name = "last_failure_at", nullable = false)
    private Instant lastFailureAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
