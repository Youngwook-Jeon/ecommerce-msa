package com.project.young.orderservice.dataaccess.entity;

import com.project.young.orderservice.application.reconciliation.OrderPaymentReconciliationManualOperation;
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
@Table(name = "order_payment_reconciliation_operation_audits")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentReconciliationOperationAuditEntity {

    @Id
    @Column(name = "id", columnDefinition = "UUID")
    private UUID id;

    @Column(name = "order_id", nullable = false, columnDefinition = "UUID")
    private UUID orderId;

    @Column(name = "operator_id", nullable = false, length = 128)
    private String operatorId;

    @Column(name = "request_id", nullable = false, columnDefinition = "UUID")
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    private OrderPaymentReconciliationManualOperation operation;

    @Column(name = "reason", length = 512)
    private String reason;

    @Column(name = "compensation_event_id", columnDefinition = "UUID")
    private UUID compensationEventId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
