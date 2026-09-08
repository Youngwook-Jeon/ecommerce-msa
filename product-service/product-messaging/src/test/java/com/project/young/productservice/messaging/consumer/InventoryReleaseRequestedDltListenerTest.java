package com.project.young.productservice.messaging.consumer;

import com.project.young.kafka.saga.dto.InventoryReleaseRequestedMessage;
import com.project.young.productservice.application.dto.command.RecordInventoryReleaseCompensationDltCommand;
import com.project.young.productservice.application.service.InventoryReleaseCompensationDltApplicationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryReleaseRequestedDltListenerTest {

    @Test
    void recordsManualFollowUpThenAcknowledgesDltRecord() {
        InventoryReleaseCompensationDltApplicationService service =
                mock(InventoryReleaseCompensationDltApplicationService.class);
        InventoryReleaseRequestedDltListener listener = new InventoryReleaseRequestedDltListener(
                service, "inventory.release.requested", "inventory.release.requested.DLT");
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        InventoryReleaseRequestedMessage message = new InventoryReleaseRequestedMessage(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "release", Instant.now(), Instant.now());
        when(service.recordManualFollowUp(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        listener.onInventoryReleaseRequestedDlt(
                message, acknowledgment, "example.DatabaseUnavailable", "down",
                "inventory.release.requested", 2, 99L);

        ArgumentCaptor<RecordInventoryReleaseCompensationDltCommand> captor =
                ArgumentCaptor.forClass(RecordInventoryReleaseCompensationDltCommand.class);
        verify(service).recordManualFollowUp(captor.capture());
        assertThat(captor.getValue()).extracting(
                RecordInventoryReleaseCompensationDltCommand::compensationEventId,
                RecordInventoryReleaseCompensationDltCommand::orderId,
                RecordInventoryReleaseCompensationDltCommand::sourceTopic,
                RecordInventoryReleaseCompensationDltCommand::dltTopic,
                RecordInventoryReleaseCompensationDltCommand::sourcePartition,
                RecordInventoryReleaseCompensationDltCommand::sourceOffset
        ).containsExactly(
                message.compensationEventId(), message.orderId(), "inventory.release.requested",
                "inventory.release.requested.DLT", 2, 99L);
        verify(acknowledgment).acknowledge();
    }
}
