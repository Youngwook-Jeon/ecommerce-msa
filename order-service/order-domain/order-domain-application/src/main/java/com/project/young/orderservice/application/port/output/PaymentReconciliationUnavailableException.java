package com.project.young.orderservice.application.port.output;

public class PaymentReconciliationUnavailableException extends RuntimeException {

    public PaymentReconciliationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
