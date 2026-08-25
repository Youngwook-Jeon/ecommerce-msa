package com.project.young.paymentservice.domain.exception;

/**
 * Payment row does not exist yet (e.g. order.created CDC lag) or was never created.
 */
public class PaymentNotFoundException extends PaymentDomainException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
