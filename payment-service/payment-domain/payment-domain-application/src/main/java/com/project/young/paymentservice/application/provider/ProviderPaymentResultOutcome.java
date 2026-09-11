package com.project.young.paymentservice.application.provider;

/**
 * Provider result semantics. A failed payment attempt is not necessarily a terminal payment.
 */
public enum ProviderPaymentResultOutcome {
    SUCCEEDED("PAYMENT_SUCCEEDED"),
    ATTEMPT_FAILED("PAYMENT_ATTEMPT_FAILED"),
    FINAL_FAILED("PAYMENT_FAILED");

    private final String providerEventType;

    ProviderPaymentResultOutcome(String providerEventType) {
        this.providerEventType = providerEventType;
    }

    public String providerEventType() {
        return providerEventType;
    }

    public boolean isTerminal() {
        return this != ATTEMPT_FAILED;
    }
}
