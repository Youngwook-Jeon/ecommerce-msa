package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentCompletedMessage;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.service.SagaCompensationApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedDltListenerTest {

    @Mock
    private SagaCompensationApplicationService sagaCompensationApplicationService;

    @Mock
    private Acknowledgment acknowledgment;

    private PaymentCompletedDltListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentCompletedDltListener(
                sagaCompensationApplicationService,
                "payment.completed",
                "payment.completed.DLT"
        );
    }

    @Test
    @DisplayName("DLT 메시지를 MANUAL 적재 커맨드로 위임하고 ack한다")
    void onPaymentCompletedDlt_recordsManualAndAcks() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentCompletedMessage message = new PaymentCompletedMessage(
                UUID.randomUUID(),
                eventId,
                paymentId,
                orderId,
                "user-1",
                "49.99",
                "USD",
                Instant.parse("2026-06-13T08:03:10.343300Z"),
                null,
                Instant.parse("2026-06-13T08:03:10.345273Z")
        );
        SagaCompensationView view = new SagaCompensationView(
                UUID.randomUUID(),
                eventId,
                paymentId,
                orderId,
                "user-1",
                "49.99",
                "USD",
                "payment.completed",
                "payment.completed.DLT",
                1,
                99L,
                "com.example.Boom",
                "boom",
                CompensationRecommendedAction.REPLAY,
                CompensationRefundSla.NONE,
                "retry_budget_exhausted_candidate_for_replay",
                CompensationHandlingStatus.MANUAL,
                Instant.now(),
                true
        );
        when(sagaCompensationApplicationService.recordManualFromDlt(any())).thenReturn(view);

        listener.onPaymentCompletedDlt(
                message,
                acknowledgment,
                "com.example.Boom",
                "boom",
                "payment.completed",
                1,
                99L
        );

        ArgumentCaptor<RecordManualCompensationCommand> captor =
                ArgumentCaptor.forClass(RecordManualCompensationCommand.class);
        verify(sagaCompensationApplicationService).recordManualFromDlt(captor.capture());
        RecordManualCompensationCommand command = captor.getValue();
        assertThat(command.eventId()).isEqualTo(eventId);
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.sourcePartition()).isEqualTo(1);
        assertThat(command.sourceOffset()).isEqualTo(99L);
        assertThat(command.failureExceptionClass()).isEqualTo("com.example.Boom");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("eventId/orderId 없으면 적재하지 않고 ack한다")
    void onPaymentCompletedDlt_missingIds_skips() {
        PaymentCompletedMessage message = new PaymentCompletedMessage(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                "user-1",
                "10.00",
                "USD",
                Instant.now(),
                null,
                Instant.now()
        );

        listener.onPaymentCompletedDlt(message, acknowledgment, null, null, null, null, null);

        verify(sagaCompensationApplicationService, never()).recordManualFromDlt(any());
        verify(acknowledgment).acknowledge();
    }
}
