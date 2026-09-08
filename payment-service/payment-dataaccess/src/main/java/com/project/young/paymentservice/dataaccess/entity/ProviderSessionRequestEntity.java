package com.project.young.paymentservice.dataaccess.entity;

import jakarta.persistence.*;
import com.project.young.paymentservice.application.provider.ProviderSessionRequestStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provider_session_requests")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSessionRequestEntity {

    @Id
    @Column(name = "payment_id", columnDefinition = "UUID")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProviderSessionRequestStatus status;

    @Column(name = "failure_message", length = 4096)
    private String failureMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;
}
