package com.project.young.orderservice.application.reconciliation;

/** Human operation that changed how an escalated payment reconciliation is handled. */
public enum OrderPaymentReconciliationManualOperation {
    REPLAY,
    CLOSE,
    REFUND
}
