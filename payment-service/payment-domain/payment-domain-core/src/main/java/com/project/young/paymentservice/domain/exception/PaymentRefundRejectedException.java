package com.project.young.paymentservice.domain.exception;

/** A refund request that will not succeed when retried unchanged. */
public class PaymentRefundRejectedException extends PaymentDomainException {

    public PaymentRefundRejectedException(String message) {
        super(message);
    }

    public PaymentRefundRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
