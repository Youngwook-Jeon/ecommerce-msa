package com.project.young.orderservice.messaging.mapper;

import java.util.UUID;

import com.project.young.kafka.saga.dto.CustomerRefundCompletedMessage;
import com.project.young.kafka.saga.dto.CustomerRefundFailedMessage;
import com.project.young.orderservice.application.dto.command.ApplyCustomerRefundResultCommand;
import org.springframework.stereotype.Component;

@Component
public class CustomerRefundResultMessageMapper {

    public ApplyCustomerRefundResultCommand toCommand(CustomerRefundCompletedMessage message) {
        validateIdentifiers(message.refundId(), message.paymentId(), message.orderId(), message.userId());
        // Older events omit resultVersion and refundCompletedAt.
        return new ApplyCustomerRefundResultCommand(message.refundId(), message.paymentId(),
                message.orderId(), message.userId(), true, null,
                message.resultVersion() == 0 ? 1 : message.resultVersion(), false,
                message.refundCompletedAt() == null ? message.occurredAt() : message.refundCompletedAt(), null);
    }

    public ApplyCustomerRefundResultCommand toCommand(CustomerRefundFailedMessage message) {
        validateIdentifiers(message.refundId(), message.paymentId(), message.orderId(), message.userId());
        // Older events omit resultVersion and refundFailedAt.
        return new ApplyCustomerRefundResultCommand(message.refundId(), message.paymentId(),
                message.orderId(), message.userId(), false, message.failureReason(),
                message.resultVersion() == 0 ? 2 : message.resultVersion(), message.failedAfterCompletion(),
                message.refundCompletedAt(),
                message.refundFailedAt() == null ? message.occurredAt() : message.refundFailedAt());
    }

    private static void validateIdentifiers(UUID refundId, UUID paymentId, UUID orderId, String userId) {
        if (refundId == null || paymentId == null || orderId == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Customer refund result has missing identifiers");
        }
    }
}
