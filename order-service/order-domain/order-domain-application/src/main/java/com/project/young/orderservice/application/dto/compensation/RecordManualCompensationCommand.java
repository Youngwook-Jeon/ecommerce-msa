package com.project.young.orderservice.application.dto.compensation;

import java.util.UUID;

/**
 * Ingest command for a payment saga DLT record.
 */
public record RecordManualCompensationCommand(
        UUID eventId,
        UUID paymentId,
        UUID orderId,
        String userId,
        String amount,
        String currency,
        String sourceTopic,
        String dltTopic,
        Integer sourcePartition,
        Long sourceOffset,
        String failureExceptionClass,
        String failureMessage
) {
}
