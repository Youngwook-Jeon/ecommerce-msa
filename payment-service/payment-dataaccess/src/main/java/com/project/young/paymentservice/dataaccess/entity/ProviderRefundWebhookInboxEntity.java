package com.project.young.paymentservice.dataaccess.entity;

import com.project.young.paymentservice.application.provider.ProviderRefundWebhookInboxStatus;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "provider_refund_webhook_inbox")
@Getter
public class ProviderRefundWebhookInboxEntity {
    @Id
    @Column(name = "event_id", length = 255)
    private String eventId;
    @Column(name = "provider_refund_id", nullable = false, length = 255)
    private String providerRefundId;
    @Column(name = "provider_payment_id", nullable = false, length = 255)
    private String providerPaymentId;
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_state", nullable = false, length = 32)
    private RefundState refundState;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProviderRefundWebhookInboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "last_error", length = 1024)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProviderRefundWebhookInboxEntity() {
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ProviderRefundWebhookInboxEntity that
                && eventId != null && eventId.equals(that.eventId);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
