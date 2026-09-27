package com.project.young.orderservice.web.refund.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CustomerRefundResponse(
        UUID refundId,
        UUID orderId,
        UUID paymentId,
        String reason,
        String status,
        String failureReason,
        Instant requestedAt,
        Instant updatedAt
) {
}
