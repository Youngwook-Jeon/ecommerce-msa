package com.project.young.orderservice.application.dto.compensation;

import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;

import java.time.Instant;
import java.util.UUID;

public record SagaCompensationView(
        UUID id,
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
        String failureMessage,
        CompensationRecommendedAction recommendedAction,
        CompensationRefundSla refundSla,
        String classificationReason,
        CompensationHandlingStatus handlingStatus,
        Instant createdAt,
        boolean newlyCreated
) {
}
