package com.project.young.orderservice.application.port.output;

/** Payment refund endpoint is unavailable, timed out, or protected by an open circuit breaker. */
public class PaymentRefundUnavailableException extends RuntimeException {
    public PaymentRefundUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
