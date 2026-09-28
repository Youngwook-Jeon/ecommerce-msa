package com.project.young.orderservice.messaging.mapper;

import java.time.Instant;
import java.util.UUID;

import com.project.young.kafka.saga.dto.CustomerRefundCompletedMessage;
import com.project.young.kafka.saga.dto.CustomerRefundFailedMessage;
import com.project.young.orderservice.application.dto.command.ApplyCustomerRefundResultCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerRefundResultMessageMapperTest {

    private final CustomerRefundResultMessageMapper mapper = new CustomerRefundResultMessageMapper();
    private final UUID refundId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final Instant occurredAt = Instant.parse("2026-09-29T00:00:00Z");
    private final Instant completedAt = occurredAt.minusSeconds(60);
    private final Instant failedAt = occurredAt.minusSeconds(30);

    @Test
    void legacyCompletion_defaultsVersionAndCompletionTime() {
        assertThat(mapper.toCommand(completed(0, null)))
                .isEqualTo(new ApplyCustomerRefundResultCommand(refundId, paymentId, orderId,
                        "user-1", true, null, 1, false, occurredAt, null));
    }

    @Test
    void legacyFailure_defaultsVersionAndFailureTime() {
        assertThat(mapper.toCommand(failed(0, false, null, null)))
                .isEqualTo(new ApplyCustomerRefundResultCommand(refundId, paymentId, orderId,
                        "user-1", false, "Refund failed", 2, false, null, occurredAt));
    }

    @Test
    void completion_preservesExplicitMetadata() {
        assertThat(mapper.toCommand(completed(1, completedAt)))
                .isEqualTo(new ApplyCustomerRefundResultCommand(refundId, paymentId, orderId,
                        "user-1", true, null, 1, false, completedAt, null));
    }

    @Test
    void lateFailure_preservesExplicitMetadata() {
        assertThat(mapper.toCommand(failed(2, true, completedAt, failedAt)))
                .isEqualTo(new ApplyCustomerRefundResultCommand(refundId, paymentId, orderId,
                        "user-1", false, "Refund failed", 2, true, completedAt, failedAt));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 2, 3})
    void completion_rejectsInvalidExplicitVersion(long version) {
        assertThatThrownBy(() -> mapper.toCommand(completed(version, completedAt)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1, 3})
    void failure_rejectsInvalidExplicitVersion(long version) {
        assertThatThrownBy(() -> mapper.toCommand(failed(version, false, null, failedAt)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lateFailureWithoutCompletionTime_isNotSilentlyCorrected() {
        assertThatThrownBy(() -> mapper.toCommand(failed(2, true, null, failedAt)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingIdentifiers_areRejectedForBothResults() {
        CustomerRefundCompletedMessage completion = new CustomerRefundCompletedMessage(
                UUID.randomUUID(), UUID.randomUUID(), null, paymentId, orderId,
                "user-1", occurredAt, occurredAt, 1, completedAt);
        CustomerRefundFailedMessage failure = new CustomerRefundFailedMessage(
                UUID.randomUUID(), UUID.randomUUID(), refundId, paymentId, orderId,
                " ", "Refund failed", occurredAt, occurredAt, 2, false, null, failedAt);

        assertThatThrownBy(() -> mapper.toCommand(completion)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toCommand(failure)).isInstanceOf(IllegalArgumentException.class);
    }

    private CustomerRefundCompletedMessage completed(long version, Instant time) {
        return new CustomerRefundCompletedMessage(UUID.randomUUID(), UUID.randomUUID(), refundId,
                paymentId, orderId, "user-1", occurredAt, occurredAt, version, time);
    }

    private CustomerRefundFailedMessage failed(long version, boolean afterCompletion,
                                               Instant completionTime, Instant failureTime) {
        return new CustomerRefundFailedMessage(UUID.randomUUID(), UUID.randomUUID(), refundId,
                paymentId, orderId, "user-1", "Refund failed", occurredAt, occurredAt,
                version, afterCompletion, completionTime, failureTime);
    }
}
