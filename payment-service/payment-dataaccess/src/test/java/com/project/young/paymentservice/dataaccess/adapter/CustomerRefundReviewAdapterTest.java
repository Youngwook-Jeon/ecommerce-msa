package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.paymentservice.application.dto.command.EscalateCustomerRefundCommand;
import com.project.young.paymentservice.dataaccess.repository.CustomerRefundReviewJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerRefundReviewAdapterTest {

    @Test
    void recordIfAbsent_truncatesDiagnosticFieldsAndReportsDuplicate() {
        CustomerRefundReviewJpaRepository repository = mock(CustomerRefundReviewJpaRepository.class);
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(repository.insertIfAbsent(eq(refundId), eq(paymentId), eq("re_pending"),
                eq("E".repeat(1024)), eq("M".repeat(4096)), any())).thenReturn(1, 0);
        EscalateCustomerRefundCommand command = new EscalateCustomerRefundCommand(
                refundId, paymentId, "re_pending", "E".repeat(1100), "M".repeat(4200));
        CustomerRefundReviewAdapter adapter = new CustomerRefundReviewAdapter(repository);

        assertThat(adapter.recordIfAbsent(command)).isTrue();
        assertThat(adapter.recordIfAbsent(command)).isFalse();
    }
}
