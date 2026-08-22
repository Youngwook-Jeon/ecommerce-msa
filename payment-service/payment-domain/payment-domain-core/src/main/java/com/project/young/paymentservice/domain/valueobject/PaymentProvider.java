package com.project.young.paymentservice.domain.valueobject;

import com.project.young.paymentservice.domain.exception.PaymentDomainException;

import java.util.Locale;
import java.util.Objects;

public enum PaymentProvider {
    STUB,
    STRIPE;

    public static PaymentProvider from(String raw) {
        Objects.requireNonNull(raw, "provider must not be null");
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return PaymentProvider.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new PaymentDomainException("Unknown payment provider: '" + raw + "'.");
        }
    }
}
