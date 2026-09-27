package com.project.young.paymentservice.application.dto.command;

import java.util.UUID;

/** {@code refundId} is the durable customer-refund and PSP idempotency key. */
public record RefundCustomerPaymentCommand(UUID refundId, UUID paymentId, UUID orderId, String userId) {
}
