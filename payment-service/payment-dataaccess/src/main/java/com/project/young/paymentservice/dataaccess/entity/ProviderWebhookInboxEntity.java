package com.project.young.paymentservice.dataaccess.entity;

import com.project.young.paymentservice.application.provider.ProviderWebhookInboxStatus;
import com.project.young.paymentservice.application.provider.ProviderPaymentResultOutcome;
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

@Entity
@Table(name = "provider_webhook_inbox")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderWebhookInboxEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_payment_id", nullable = false, length = 255)
    private String providerPaymentId;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private ProviderPaymentResultOutcome outcome;

    @Column(name = "failure_reason", length = 4096)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProviderWebhookInboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "next_retry_at", nullable = false)
    private Instant nextRetryAt;

    @Column(name = "last_failure_message", length = 4096)
    private String lastFailureMessage;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
