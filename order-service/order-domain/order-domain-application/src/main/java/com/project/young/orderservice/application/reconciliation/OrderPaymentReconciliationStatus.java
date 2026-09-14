package com.project.young.orderservice.application.reconciliation;

public enum OrderPaymentReconciliationStatus {
    RETRYING,
    ESCALATED,
    REFUND_REQUESTED,
    RESOLVED
}
