package com.project.young.paymentservice.domain.exception;

/** The original PSP attempt can no longer be safely retried without checking its outcome. */
public class PaymentRefundNeedsReviewException extends PaymentRefundRejectedException {

    public PaymentRefundNeedsReviewException(String message) {
        super(message);
    }
}
