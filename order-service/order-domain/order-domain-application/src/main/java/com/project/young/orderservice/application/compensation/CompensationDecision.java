package com.project.young.orderservice.application.compensation;

/**
 * Classification result encoding compensation policies for a DLT failure.
 */
public record CompensationDecision(
        CompensationRecommendedAction recommendedAction,
        CompensationRefundSla refundSla,
        String reason
) {
}
