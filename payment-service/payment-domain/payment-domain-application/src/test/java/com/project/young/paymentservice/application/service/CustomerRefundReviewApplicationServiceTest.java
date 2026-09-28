package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.EscalateCustomerRefundCommand;
import com.project.young.paymentservice.application.port.output.CustomerRefundReviewPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class CustomerRefundReviewApplicationServiceTest {

    @Test
    void escalate_isIdempotentAndReportsWhetherCreated() {
        CustomerRefundReviewPort port = mock(CustomerRefundReviewPort.class);
        EscalateCustomerRefundCommand command = new EscalateCustomerRefundCommand(
                UUID.randomUUID(), UUID.randomUUID(), null, "NeedsReview", "Unknown PSP outcome");
        when(port.recordIfAbsent(command)).thenReturn(true, false);
        CustomerRefundReviewApplicationService service = new CustomerRefundReviewApplicationService(port);

        assertThat(service.escalate(command)).isTrue();
        assertThat(service.escalate(command)).isFalse();
    }

    @Test
    void escalate_rejectsMissingCommand() {
        CustomerRefundReviewPort port = mock(CustomerRefundReviewPort.class);
        assertThatThrownBy(() -> new CustomerRefundReviewApplicationService(port).escalate(null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(port);
    }
}
