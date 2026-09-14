package com.project.young.paymentservice.application.dto.command;

import java.util.UUID;

public record RecordOrderCreatedDltCommand(
        UUID eventId,
        UUID orderId,
        String userId,
        String totalAmount,
        String currency,
        String sourceTopic,
        String dltTopic,
        Integer sourcePartition,
        Long sourceOffset,
        String failureExceptionClass,
        String failureMessage
) {
}
