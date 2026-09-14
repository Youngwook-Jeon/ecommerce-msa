package com.project.young.common.application.contract.payment;

/** Stable Payment-to-Order integration contract for order-status reconciliation. */
public enum PaymentReconciliationStatus {
    PENDING,
    COMPLETED,
    FAILED
}
