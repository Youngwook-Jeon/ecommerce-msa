package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.port.output.RefundCompensationDltPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundCompensationDltApplicationServiceTest {

    @Test
    void recordManualFollowUp_delegatesIdempotentInsert() {
        RefundCompensationDltPort port = mock(RefundCompensationDltPort.class);
        RefundCompensationDltApplicationService service = new RefundCompensationDltApplicationService(port);
        RecordRefundCompensationDltCommand command = command();
        when(port.recordIfAbsent(command)).thenReturn(true);

        boolean recorded = service.recordManualFollowUp(command);

        assertThat(recorded).isTrue();
        verify(port).recordIfAbsent(command);
    }

    private static RecordRefundCompensationDltCommand command() {
        return new RecordRefundCompensationDltCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "payment.refund.requested", "payment.refund.requested.DLT", 1, 42L,
                "example.ProviderUnavailable", "provider unavailable"
        );
    }
}
