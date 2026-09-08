package com.project.young.orderservice.application.compensation;

/**
 * Policy recommendation for a DLT record. This slice only persists MANUAL handling;
 * automation (replay / refund) will consume these values later.
 */
public enum CompensationRecommendedAction {
    /** Transient failure exhausted retries; safe to redrive confirmPayment later. */
    REPLAY,
    /**
     * Money captured but order cannot complete (e.g. inventory expired).
     * Policy: refund only — do not re-reserve / re-confirm.
     */
    REFUND,
    /** Payment failed, but the checkout soft-hold still needs to be released. */
    RELEASE_INVENTORY,
    /** Needs human judgment. */
    MANUAL
}
