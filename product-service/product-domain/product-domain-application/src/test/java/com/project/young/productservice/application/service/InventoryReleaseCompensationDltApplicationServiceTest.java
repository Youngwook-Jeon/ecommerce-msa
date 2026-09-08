package com.project.young.productservice.application.service;

import com.project.young.productservice.application.dto.command.RecordInventoryReleaseCompensationDltCommand;
import com.project.young.productservice.application.port.output.InventoryReleaseCompensationDltPort;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReleaseCompensationDltApplicationServiceTest {

    @Test
    void recordsManualFollowUpIdempotently() {
        InventoryReleaseCompensationDltPort port = mock(InventoryReleaseCompensationDltPort.class);
        InventoryReleaseCompensationDltApplicationService service =
                new InventoryReleaseCompensationDltApplicationService(port);
        RecordInventoryReleaseCompensationDltCommand command = command();
        when(port.recordIfAbsent(command)).thenReturn(true);

        boolean recorded = service.recordManualFollowUp(command);

        assertThat(recorded).isTrue();
        verify(port).recordIfAbsent(command);
    }

    private static RecordInventoryReleaseCompensationDltCommand command() {
        return new RecordInventoryReleaseCompensationDltCommand(
                UUID.randomUUID(), UUID.randomUUID(), "inventory.release.requested",
                "inventory.release.requested.DLT", 1, 17L, "example.RetryableFailure", "down");
    }
}
