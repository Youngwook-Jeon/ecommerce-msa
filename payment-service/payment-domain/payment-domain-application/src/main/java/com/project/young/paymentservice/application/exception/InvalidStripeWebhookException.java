package com.project.young.paymentservice.application.exception;

/**
 * Raised when Stripe webhook signature verification or payload parsing fails.
 * Controllers map this to HTTP 400.
 */
public class InvalidStripeWebhookException extends RuntimeException {

    public InvalidStripeWebhookException(String message) {
        super(message);
    }

    public InvalidStripeWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
