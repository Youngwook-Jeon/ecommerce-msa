package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.OrderCreatedMessage;
import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;
import com.project.young.paymentservice.application.service.OrderCreatedDltApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreatedDltListenerTest {

    @Test
    void onDlt_recordsManualReplayItemAndAcknowledges() {
        OrderCreatedDltApplicationService service = mock(OrderCreatedDltApplicationService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        OrderCreatedDltListener listener = new OrderCreatedDltListener(service, "order.created", "order.created.DLT");
        OrderCreatedMessage message = new OrderCreatedMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "user-1", "49.99", "USD", Instant.now(), null, Instant.now());
        when(service.recordManualFollowUp(any())).thenReturn(true);

        listener.onDlt(message, acknowledgment, "example.DatabaseFailure", "database unavailable", "order.created", 1, 42L);

        var captor = org.mockito.ArgumentCaptor.forClass(RecordOrderCreatedDltCommand.class);
        verify(service).recordManualFollowUp(captor.capture());
        assertThat(captor.getValue()).extracting(RecordOrderCreatedDltCommand::eventId,
                RecordOrderCreatedDltCommand::orderId, RecordOrderCreatedDltCommand::sourceOffset)
                .containsExactly(message.eventId(), message.orderId(), 42L);
        verify(acknowledgment).acknowledge();
    }
}
