package com.project.young.orderservice.application.compensation;

/**
 * Refund timing policy when {@link CompensationRecommendedAction#REFUND} applies.
 * Policy: DLT → immediate auto-refund once refund automation is enabled.
 */
public enum CompensationRefundSla {
    IMMEDIATE,
    NONE
}
