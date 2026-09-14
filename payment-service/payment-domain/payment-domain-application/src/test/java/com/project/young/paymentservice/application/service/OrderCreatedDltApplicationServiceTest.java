package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreatedDltApplicationServiceTest {

    @Test
    void recordManualFollowUp_delegatesIdempotentInsert() {
        OrderCreatedDltPort port = mock(OrderCreatedDltPort.class);
        OrderCreatedDltApplicationService service = new OrderCreatedDltApplicationService(port);
        RecordOrderCreatedDltCommand command = command();
        when(port.recordIfAbsent(command)).thenReturn(true);

        boolean recorded = service.recordManualFollowUp(command);

        assertThat(recorded).isTrue();
        verify(port).recordIfAbsent(command);
    }

    private static RecordOrderCreatedDltCommand command() {
        return new RecordOrderCreatedDltCommand(
                UUID.randomUUID(), UUID.randomUUID(), "customer-1", "10000.00", "KRW",
                "order.created", "order.created.DLT", 1, 42L,
                "example.ProviderUnavailable", "provider unavailable"
        );
    }
}
