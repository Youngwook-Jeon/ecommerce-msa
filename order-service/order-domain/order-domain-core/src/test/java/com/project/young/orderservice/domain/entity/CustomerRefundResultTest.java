package com.project.young.orderservice.domain.entity;

import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerRefundResultTest {
    private static final Instant REQUESTED = Instant.parse("2026-09-28T00:00:00Z");
    private static final Instant COMPLETED = REQUESTED.plusSeconds(60);
    private static final Instant FAILED = COMPLETED.plusSeconds(60);

    @Test
    void completedThenFailed_preservesBothObservationTimes() {
        CustomerRefund refund = requested();
        assertThat(refund.applyProviderResult(true, 1, false, null, COMPLETED, null, COMPLETED)).isTrue();
        assertThat(refund.applyProviderResult(false, 2, true, "bank rejected", COMPLETED, FAILED, FAILED)).isTrue();
        assertLateFailure(refund);
    }

    @Test
    void correctionBeforeCompletion_doesNotRequireOrderToHaveReceivedSuccess() {
        CustomerRefund refund = requested();
        assertThat(refund.applyProviderResult(false, 2, true, "bank rejected", COMPLETED, FAILED, FAILED)).isTrue();
        assertThat(refund.applyProviderResult(true, 1, false, null, COMPLETED, null, FAILED.plusSeconds(60))).isFalse();
        assertLateFailure(refund);
    }

    @Test
    void duplicateCorrection_isNoOp() {
        CustomerRefund refund = requested();
        refund.applyProviderResult(false, 2, true, "bank rejected", COMPLETED, FAILED, FAILED);
        assertThat(refund.applyProviderResult(false, 2, true, "bank rejected", COMPLETED, FAILED,
                FAILED.plusSeconds(60))).isFalse();
        assertThat(refund.getUpdatedAt()).isEqualTo(FAILED);
    }

    @Test
    void initialFailure_remainsDistinctFromLateFailure() {
        CustomerRefund refund = requested();
        refund.applyProviderResult(false, 2, false, "bank rejected", null, FAILED, FAILED);
        assertThat(refund.getStatus()).isEqualTo(CustomerRefundStatus.FAILED);
        assertThat(refund.getCompletedAt()).isNull();
        assertThat(refund.applyProviderResult(true, 1, false, null, COMPLETED, null, FAILED)).isFalse();
    }

    private static CustomerRefund requested() {
        return CustomerRefund.request(new CustomerRefundId(UUID.randomUUID()), new OrderId(UUID.randomUUID()),
                UUID.randomUUID(), new UserId("user-1"), "no longer needed", REQUESTED);
    }

    @Test
    void duplicateDomainTransitions_doNotChangeObservationHistory() {
        CustomerRefund completed = requested();
        completed.complete(COMPLETED);
        completed.complete(FAILED);
        assertThat(completed.getCompletedAt()).isEqualTo(COMPLETED);
        CustomerRefund failed = requested();
        failed.fail("bank rejected", FAILED);
        failed.fail("other reason", FAILED.plusSeconds(60));
        assertThat(failed.getFailedAt()).isEqualTo(FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("bank rejected");
    }

    private static void assertLateFailure(CustomerRefund refund) {
        assertThat(refund.getStatus()).isEqualTo(CustomerRefundStatus.FAILED_AFTER_COMPLETION);
        assertThat(refund.getResultVersion()).isEqualTo(2);
        assertThat(refund.getCompletedAt()).isEqualTo(COMPLETED);
        assertThat(refund.getFailedAt()).isEqualTo(FAILED);
        assertThat(refund.getFailureReason()).isEqualTo("bank rejected");
    }
}
