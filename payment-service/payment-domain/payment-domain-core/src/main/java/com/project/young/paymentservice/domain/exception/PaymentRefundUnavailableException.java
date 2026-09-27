package com.project.young.paymentservice.domain.exception;

/** An uncertain or transient PSP refund result; retry with the same idempotency key. */
public class PaymentRefundUnavailableException extends PaymentDomainException {

    public PaymentRefundUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
