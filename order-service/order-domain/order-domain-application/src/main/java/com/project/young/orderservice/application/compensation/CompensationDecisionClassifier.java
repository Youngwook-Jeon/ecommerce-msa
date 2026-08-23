package com.project.young.orderservice.application.compensation;

import com.project.young.orderservice.application.port.output.InventoryReservationClientException;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.domain.exception.OrderCheckoutValidationException;
import com.project.young.orderservice.domain.exception.OrderIllegalTransitionException;
import com.project.young.orderservice.domain.exception.OrderNotFoundException;

/**
 * Maps DLT failure metadata to a recommended action.
 * <p>
 * Policies encoded here:
 * <ul>
 *   <li>Inventory expiry / inventory reject → {@link CompensationRecommendedAction#REFUND} only
 *       (no re-reserve / confirm).</li>
 *   <li>When REFUND applies, {@link CompensationRefundSla#IMMEDIATE} (DLT → auto-refund when
 *       automation is enabled).</li>
 * </ul>
 * This slice only persists MANUAL handling; it does not execute refund/replay yet.
 */
public final class CompensationDecisionClassifier {

    private CompensationDecisionClassifier() {
    }

    public static CompensationDecision classify(String failureExceptionClass, String failureMessage) {
        String fqcn = nullToEmpty(failureExceptionClass);
        String message = nullToEmpty(failureMessage).toLowerCase();

        if (isType(fqcn, InventoryReservationConflictException.class)
                || isType(fqcn, InventoryReservationClientException.class)
                || looksLikeInventoryExpiry(message)) {
            return new CompensationDecision(
                    CompensationRecommendedAction.REFUND,
                    CompensationRefundSla.IMMEDIATE,
                    "inventory_unavailable_or_expired_refund_only"
            );
        }

        if (isType(fqcn, OrderIllegalTransitionException.class)) {
            // Payment already captured; illegal confirm transition → refund, do not force confirm.
            return new CompensationDecision(
                    CompensationRecommendedAction.REFUND,
                    CompensationRefundSla.IMMEDIATE,
                    "illegal_order_transition_after_payment_refund"
            );
        }

        if (isType(fqcn, OrderNotFoundException.class)
                || isType(fqcn, OrderCheckoutValidationException.class)) {
            return new CompensationDecision(
                    CompensationRecommendedAction.MANUAL,
                    CompensationRefundSla.NONE,
                    "non_recoverable_order_lookup_or_validation"
            );
        }

        if (fqcn.isBlank()) {
            return new CompensationDecision(
                    CompensationRecommendedAction.MANUAL,
                    CompensationRefundSla.NONE,
                    "missing_exception_metadata"
            );
        }

        // Exhausted retries on typically transient failures → recommend replay later.
        return new CompensationDecision(
                CompensationRecommendedAction.REPLAY,
                CompensationRefundSla.NONE,
                "retry_budget_exhausted_candidate_for_replay"
        );
    }

    private static boolean looksLikeInventoryExpiry(String lowerMessage) {
        return lowerMessage.contains("expir")
                || (lowerMessage.contains("reservation") && lowerMessage.contains("not found"))
                || (lowerMessage.contains("hold") && lowerMessage.contains("expired"));
    }

    private static boolean isType(String fqcn, Class<?> type) {
        return fqcn.equals(type.getName()) || fqcn.endsWith("." + type.getSimpleName());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
