package com.project.young.paymentservice.application.dto.command;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.domain.entity.Payment;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ProcessPaymentCommand(
        UUID orderId,
        String userId,
        Money amount,
        String currency
) {
    public ProcessPaymentCommand {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        if (currency == null || currency.isBlank()) {
            currency = Payment.DEFAULT_CURRENCY;
        }
    }

    public static ProcessPaymentCommand fromOrderCreated(
            UUID orderId,
            String userId,
            String totalAmount,
            String currency
    ) {
        Objects.requireNonNull(totalAmount, "totalAmount must not be null");
        return new ProcessPaymentCommand(
                orderId,
                userId,
                new Money(new BigDecimal(totalAmount)),
                currency
        );
    }
}
