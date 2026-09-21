package com.project.young.orderservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.orderservice.domain.exception.CustomerRefundDomainException;
import com.project.young.orderservice.domain.exception.CustomerRefundStateConflictException;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Aggregate root for one full customer refund request per order. */
public class CustomerRefund extends AggregateRoot<CustomerRefundId> {

    public static final int MAX_REASON_LENGTH = 512;
    public static final int MAX_FAILURE_REASON_LENGTH = 512;

    private final OrderId orderId;
    private final UUID paymentId;
    private final UserId userId;
    private final String reason;
    private CustomerRefundStatus status;
    private String failureReason;
    private final Instant requestedAt;
    private Instant updatedAt;

    private CustomerRefund(
            CustomerRefundId refundId,
            OrderId orderId,
            UUID paymentId,
            UserId userId,
            String reason,
            CustomerRefundStatus status,
            String failureReason,
            Instant requestedAt,
            Instant updatedAt
    ) {
        super.setId(refundId);
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.userId = userId;
        this.reason = reason;
        this.status = status;
        this.failureReason = failureReason;
        this.requestedAt = requestedAt;
        this.updatedAt = updatedAt;
    }

    public static CustomerRefund request(
            CustomerRefundId refundId,
            OrderId orderId,
            UUID paymentId,
            UserId userId,
            String reason,
            Instant requestedAt
    ) {
        return restore(
                refundId,
                orderId,
                paymentId,
                userId,
                reason,
                CustomerRefundStatus.REQUESTED,
                null,
                requestedAt,
                requestedAt
        );
    }

    public static CustomerRefund restore(
            CustomerRefundId refundId,
            OrderId orderId,
            UUID paymentId,
            UserId userId,
            String reason,
            CustomerRefundStatus status,
            String failureReason,
            Instant requestedAt,
            Instant updatedAt
    ) {
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        return new CustomerRefund(
                refundId,
                orderId,
                paymentId,
                userId,
                normalizeRequiredReason(reason, "reason", MAX_REASON_LENGTH),
                status,
                normalizeOptionalReason(failureReason, "failureReason", MAX_FAILURE_REASON_LENGTH),
                requestedAt,
                updatedAt
        );
    }

    public void complete(Instant completedAt) {
        transitionFromRequested(CustomerRefundStatus.COMPLETED, null, completedAt);
    }

    public void fail(String failureReason, Instant failedAt) {
        transitionFromRequested(
                CustomerRefundStatus.FAILED,
                normalizeRequiredReason(failureReason, "failureReason", MAX_FAILURE_REASON_LENGTH),
                failedAt
        );
    }

    public void close(Instant closedAt) {
        if (status == CustomerRefundStatus.CLOSED) {
            return;
        }
        if (status == CustomerRefundStatus.COMPLETED) {
            throw new CustomerRefundStateConflictException("Completed customer refund cannot be closed.");
        }
        status = CustomerRefundStatus.CLOSED;
        updatedAt = requireTransitionTime(closedAt);
    }

    private void transitionFromRequested(CustomerRefundStatus targetStatus, String targetFailureReason, Instant at) {
        if (status == targetStatus) {
            return;
        }
        if (status != CustomerRefundStatus.REQUESTED) {
            throw new CustomerRefundStateConflictException(
                    "Cannot transition customer refund from " + status + " to " + targetStatus + ".");
        }
        status = targetStatus;
        failureReason = targetFailureReason;
        updatedAt = requireTransitionTime(at);
    }

    private Instant requireTransitionTime(Instant at) {
        Objects.requireNonNull(at, "transition time must not be null");
        if (at.isBefore(requestedAt)) {
            throw new CustomerRefundDomainException("Refund transition time cannot be before requestedAt.");
        }
        return at;
    }

    private static String normalizeRequiredReason(String value, String fieldName, int maxLength) {
        String normalized = normalizeOptionalReason(value, fieldName, maxLength);
        if (normalized == null) {
            throw new CustomerRefundDomainException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private static String normalizeOptionalReason(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new CustomerRefundDomainException(fieldName + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public CustomerRefundStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
