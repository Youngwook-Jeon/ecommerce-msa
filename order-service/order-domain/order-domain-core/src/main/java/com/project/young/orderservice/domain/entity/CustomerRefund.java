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
    private long resultVersion;
    private Instant completedAt;
    private Instant failedAt;

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
        this.resultVersion = status == CustomerRefundStatus.COMPLETED ? 1
                : status == CustomerRefundStatus.FAILED || status == CustomerRefundStatus.FAILED_AFTER_COMPLETION ? 2 : 0;
        this.completedAt = status == CustomerRefundStatus.COMPLETED ? updatedAt : null;
        this.failedAt = status == CustomerRefundStatus.FAILED ? updatedAt : null;
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
        if (status == CustomerRefundStatus.COMPLETED) {
            return;
        }
        transitionFromRequested(CustomerRefundStatus.COMPLETED, null, completedAt);
        this.completedAt = completedAt;
        this.resultVersion = 1;
    }

    public void fail(String failureReason, Instant failedAt) {
        if (status == CustomerRefundStatus.FAILED) {
            return;
        }
        transitionFromRequested(
                CustomerRefundStatus.FAILED,
                normalizeRequiredReason(failureReason, "failureReason", MAX_FAILURE_REASON_LENGTH),
                failedAt
        );
        this.failedAt = failedAt;
        this.resultVersion = 2;
    }

    public static CustomerRefund restore(CustomerRefundId refundId, OrderId orderId, UUID paymentId,
                                         UserId userId, String reason, CustomerRefundStatus status,
                                         String failureReason, Instant requestedAt, Instant updatedAt,
                                         long resultVersion, Instant completedAt, Instant failedAt) {
        if (resultVersion < 0) {
            throw new CustomerRefundDomainException("Result version must not be negative.");
        }
        CustomerRefund refund = restore(refundId, orderId, paymentId, userId, reason, status,
                failureReason, requestedAt, updatedAt);
        refund.resultVersion = resultVersion;
        refund.completedAt = completedAt;
        refund.failedAt = failedAt;
        return refund;
    }

    /** Failure has precedence over success for this single-refund protocol, independent of delivery order. */
    public boolean applyProviderResult(boolean succeeded, long version, boolean afterCompletion,
                                       String reason, Instant providerCompletedAt, Instant providerFailedAt, Instant at) {
        if (version != (succeeded ? 1 : 2) || (succeeded && afterCompletion)
                || (afterCompletion && providerCompletedAt == null)) {
            throw new CustomerRefundDomainException("Invalid provider refund result metadata.");
        }
        if (version <= resultVersion) {
            return false;
        }
        if (status == CustomerRefundStatus.CLOSED) {
            throw new CustomerRefundStateConflictException("Closed customer refund requires operator review.");
        }
        if (succeeded && (status == CustomerRefundStatus.FAILED || status == CustomerRefundStatus.FAILED_AFTER_COMPLETION)) {
            return false;
        }
        Instant transitionAt = requireTransitionTime(at);
        if (succeeded) {
            status = CustomerRefundStatus.COMPLETED;
            completedAt = providerCompletedAt == null ? at : providerCompletedAt;
            failureReason = null;
        } else {
            failureReason = normalizeRequiredReason(reason, "failureReason", MAX_FAILURE_REASON_LENGTH);
            boolean lateFailure = afterCompletion || status == CustomerRefundStatus.COMPLETED;
            status = lateFailure ? CustomerRefundStatus.FAILED_AFTER_COMPLETION : CustomerRefundStatus.FAILED;
            if (providerCompletedAt != null) {
                completedAt = providerCompletedAt;
            }
            failedAt = providerFailedAt == null ? at : providerFailedAt;
        }
        resultVersion = version;
        updatedAt = transitionAt;
        return true;
    }

    public long getResultVersion() {
        return resultVersion;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void close(Instant closedAt) {
        if (status == CustomerRefundStatus.CLOSED) {
            return;
        }
        if (status == CustomerRefundStatus.COMPLETED) {
            throw new CustomerRefundStateConflictException("Completed customer refund cannot be closed.");
        }
        Instant transitionAt = requireTransitionTime(closedAt);
        status = CustomerRefundStatus.CLOSED;
        updatedAt = transitionAt;
    }

    private void transitionFromRequested(CustomerRefundStatus targetStatus, String targetFailureReason, Instant at) {
        if (status == targetStatus) {
            return;
        }
        if (status != CustomerRefundStatus.REQUESTED) {
            throw new CustomerRefundStateConflictException(
                    "Cannot transition customer refund from " + status + " to " + targetStatus + ".");
        }
        Instant transitionAt = requireTransitionTime(at);
        status = targetStatus;
        failureReason = targetFailureReason;
        updatedAt = transitionAt;
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
