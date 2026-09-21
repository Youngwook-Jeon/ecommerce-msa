package com.project.young.orderservice.domain.entity;

import com.project.young.orderservice.domain.exception.CustomerRefundDomainException;
import com.project.young.orderservice.domain.exception.CustomerRefundStateConflictException;
import com.project.young.orderservice.domain.valueobject.CustomerRefundId;
import com.project.young.orderservice.domain.valueobject.CustomerRefundStatus;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerRefundTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-22T00:00:00Z");

    @Test
    void request_trimsReasonAndStartsRequested() {
        CustomerRefund refund = customerRefund("  duplicate delivery charge  ");

        assertThat(refund.getStatus()).isEqualTo(CustomerRefundStatus.REQUESTED);
        assertThat(refund.getReason()).isEqualTo("duplicate delivery charge");
        assertThat(refund.getFailureReason()).isNull();
        assertThat(refund.getRequestedAt()).isEqualTo(REQUESTED_AT);
    }

    @Test
    void request_rejectsBlankReason() {
        assertThatThrownBy(() -> customerRefund("  "))
                .isInstanceOf(CustomerRefundDomainException.class)
                .hasMessage("reason must not be blank.");
    }

    @Test
    void complete_allowsOnlyRequestedRefund() {
        CustomerRefund refund = customerRefund("duplicate delivery charge");
        Instant completedAt = REQUESTED_AT.plusSeconds(10);

        refund.complete(completedAt);

        assertThat(refund.getStatus()).isEqualTo(CustomerRefundStatus.COMPLETED);
        assertThat(refund.getUpdatedAt()).isEqualTo(completedAt);
        assertThatThrownBy(() -> refund.fail("PSP timeout", completedAt.plusSeconds(1)))
                .isInstanceOf(CustomerRefundStateConflictException.class);
    }

    private CustomerRefund customerRefund(String reason) {
        return CustomerRefund.request(
                new CustomerRefundId(UUID.randomUUID()),
                new OrderId(UUID.randomUUID()),
                UUID.randomUUID(),
                new UserId("customer-1"),
                reason,
                REQUESTED_AT
        );
    }
}
