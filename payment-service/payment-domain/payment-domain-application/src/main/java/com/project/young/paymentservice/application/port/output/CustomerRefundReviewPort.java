package com.project.young.paymentservice.application.port.output;

import java.util.UUID;

import com.project.young.paymentservice.application.dto.command.EscalateCustomerRefundCommand;

/** Separate durable operational queue for customer refunds with an unconfirmed outcome. */
public interface CustomerRefundReviewPort {
    boolean recordIfAbsent(EscalateCustomerRefundCommand command);

    void recordConfirmedLateFailure(UUID refundId, UUID paymentId,
                                    String providerRefundId, String reason);
}
