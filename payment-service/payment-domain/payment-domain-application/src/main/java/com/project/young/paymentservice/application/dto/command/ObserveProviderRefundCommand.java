package com.project.young.paymentservice.application.dto.command;

import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import java.util.Objects;

public record ObserveProviderRefundCommand(
        String eventId, String providerRefundId, String providerPaymentId, RefundState state, String failureReason
) {
    public ObserveProviderRefundCommand {
        if (eventId == null || eventId.isBlank() || providerRefundId == null || providerRefundId.isBlank()
                || providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("Refund webhook identifiers must not be blank");
        }
        Objects.requireNonNull(state, "state must not be null");
    }
}
