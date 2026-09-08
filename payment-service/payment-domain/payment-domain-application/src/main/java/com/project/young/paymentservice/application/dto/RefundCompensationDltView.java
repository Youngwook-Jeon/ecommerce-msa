package com.project.young.paymentservice.application.dto;

import java.time.Instant;
import java.util.UUID;

public record RefundCompensationDltView(UUID compensationEventId, UUID paymentId, UUID orderId, Instant createdAt, int replayAttempts) { }
