package com.project.young.paymentservice.application.dto.command;

import java.util.UUID;

/**
 * Idempotent refund command originating from a saga compensation request.
 */
public record RefundPaymentCommand(
        UUID compensationEventId,
        UUID paymentId,
        UUID orderId
) {
}
