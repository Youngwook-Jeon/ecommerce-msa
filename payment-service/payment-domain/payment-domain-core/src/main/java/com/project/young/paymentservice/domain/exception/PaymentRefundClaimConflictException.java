package com.project.young.paymentservice.domain.exception;

/** Another request already owns this payment's refund attempt. */
public class PaymentRefundClaimConflictException extends PaymentRefundRejectedException {

    public PaymentRefundClaimConflictException(String message) {
        super(message);
    }
}
