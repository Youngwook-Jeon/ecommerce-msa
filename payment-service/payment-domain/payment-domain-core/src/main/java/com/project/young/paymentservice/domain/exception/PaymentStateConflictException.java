package com.project.young.paymentservice.domain.exception;

/**
 * Thrown when a payment status transition is illegal or lost a concurrent compare-and-set.
 */
public class PaymentStateConflictException extends PaymentDomainException {

    public PaymentStateConflictException(String message) {
        super(message);
    }

    public PaymentStateConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
