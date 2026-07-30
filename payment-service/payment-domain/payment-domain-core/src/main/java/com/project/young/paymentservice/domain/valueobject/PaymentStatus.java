package com.project.young.paymentservice.domain.valueobject;

public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this != PENDING;
    }

    /**
     * Same-status is allowed (idempotent). Non-terminal {@link #PENDING} may move to any terminal status.
     */
    public boolean canTransitionTo(PaymentStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return this == PENDING && target.isTerminal();
    }
}
