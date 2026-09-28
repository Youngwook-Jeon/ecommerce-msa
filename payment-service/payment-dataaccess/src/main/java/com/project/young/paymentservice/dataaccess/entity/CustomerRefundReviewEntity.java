package com.project.young.paymentservice.dataaccess.entity;

import com.project.young.paymentservice.application.refund.CustomerRefundReviewStatus;
import com.project.young.paymentservice.application.refund.CustomerRefundReviewReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_refund_reviews")
@Getter
public class CustomerRefundReviewEntity {
    @Id
    @Column(name = "refund_id", columnDefinition = "UUID")
    private UUID refundId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "UUID")
    private UUID paymentId;

    @Column(name = "provider_refund_id", length = 255)
    private String providerRefundId;

    @Column(name = "handling_status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private CustomerRefundReviewStatus handlingStatus;

    @Column(name = "review_reason", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private CustomerRefundReviewReason reviewReason;

    @Column(name = "confirmed_failure_reason", length = 500)
    private String confirmedFailureReason;

    @Column(name = "confirmed_failed_at")
    private Instant confirmedFailedAt;

    @Column(name = "failure_exception_class", nullable = false, length = 1024)
    private String failureExceptionClass;

    @Column(name = "failure_message", length = 4096)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CustomerRefundReviewEntity() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof CustomerRefundReviewEntity that
                && refundId != null && refundId.equals(that.refundId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
