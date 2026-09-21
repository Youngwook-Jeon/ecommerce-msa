package com.project.young.orderservice.application.dto;

import com.project.young.orderservice.domain.entity.CustomerRefund;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;

import java.time.Instant;
import java.util.UUID;

public record CustomerRefundView(
        UUID refundId,
        UUID orderId,
        UUID paymentId,
        String userId,
        String reason,
        CustomerRefundStatus status,
        String failureReason,
        Instant requestedAt,
        Instant updatedAt
) {

    public static CustomerRefundView from(CustomerRefund customerRefund) {
        return new CustomerRefundView(
                customerRefund.getId().getValue(),
                customerRefund.getOrderId().getValue(),
                customerRefund.getPaymentId(),
                customerRefund.getUserId().value(),
                customerRefund.getReason(),
                customerRefund.getStatus(),
                customerRefund.getFailureReason(),
                customerRefund.getRequestedAt(),
                customerRefund.getUpdatedAt()
        );
    }
}
