package com.project.young.orderservice.application.dto.command;

import java.util.UUID;

public record ApplyCustomerRefundResultCommand(
        UUID refundId, UUID paymentId, UUID orderId, String userId,
        boolean succeeded, String failureReason
) {
}
