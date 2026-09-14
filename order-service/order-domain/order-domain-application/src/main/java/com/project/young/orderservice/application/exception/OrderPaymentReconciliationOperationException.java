package com.project.young.orderservice.application.exception;

/** Raised when an operations action cannot be applied to the current reconciliation state. */
public class OrderPaymentReconciliationOperationException extends RuntimeException {

    public OrderPaymentReconciliationOperationException(String message) {
        super(message);
    }
}
