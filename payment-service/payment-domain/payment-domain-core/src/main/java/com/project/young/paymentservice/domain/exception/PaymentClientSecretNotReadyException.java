package com.project.young.paymentservice.domain.exception;

/**
 * Payment exists but provider clientSecret is not available yet (session still being created).
 */
public class PaymentClientSecretNotReadyException extends PaymentDomainException {

    public PaymentClientSecretNotReadyException(String message) {
        super(message);
    }
}
