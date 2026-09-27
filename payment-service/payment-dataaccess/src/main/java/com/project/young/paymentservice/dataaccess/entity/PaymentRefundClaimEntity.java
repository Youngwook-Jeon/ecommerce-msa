package com.project.young.paymentservice.dataaccess.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refund_claims")
@Getter
public class PaymentRefundClaimEntity {

    @Id
    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "request_id", nullable = false, columnDefinition = "UUID")
    private UUID requestId;

    @Column(name = "request_kind", nullable = false, length = 32)
    private String requestKind;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "first_attempt_at")
    private Instant firstAttemptAt;

    protected PaymentRefundClaimEntity() {
    }
}
