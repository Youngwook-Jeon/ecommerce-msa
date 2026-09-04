package com.project.young.orderservice.application.port.output;

/** Payment service rejected a refund request permanently (non-rate-limited 4xx). */
public class PaymentRefundClientException extends RuntimeException {
    public PaymentRefundClientException(String message) {
        super(message);
    }
}
