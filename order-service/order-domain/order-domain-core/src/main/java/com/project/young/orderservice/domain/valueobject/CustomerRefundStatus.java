package com.project.young.orderservice.domain.valueobject;

/**
 * Lifecycle of a customer-initiated refund. This is deliberately independent
 * from {@link OrderStatus}; a confirmed order does not become REFUNDED merely
 * because a refund request was accepted.
 */
public enum CustomerRefundStatus {
    REQUESTED,
    COMPLETED,
    FAILED,
    CLOSED
}
