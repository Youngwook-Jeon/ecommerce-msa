package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentFailedMessage;
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
class PaymentFailedDltListenerTest {

    @Mock
    private SagaCompensationApplicationService sagaCompensationApplicationService;

    @Mock
    private Acknowledgment acknowledgment;

    private PaymentFailedDltListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentFailedDltListener(
                sagaCompensationApplicationService,
                "payment.failed",
                "payment.failed.DLT"
        );
    }

    @Test
    @DisplayName("DLT 메시지를 MANUAL 적재 커맨드로 위임하고 ack한다")
    void onPaymentFailedDlt_recordsManualAndAcks() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentFailedMessage message = new PaymentFailedMessage(
                UUID.randomUUID(),
                eventId,
                paymentId,
                orderId,
                "user-1",
                "49.99",
                "card declined",
                Instant.parse("2026-06-13T08:03:10.343300Z"),
                null,
                Instant.parse("2026-06-13T08:03:10.345273Z")
        );
        SagaCompensationView view = new SagaCompensationView(
                UUID.randomUUID(), eventId, paymentId, orderId, "user-1", "49.99", null,
                "payment.failed", "payment.failed.DLT", 1, 99L,
                "com.example.Boom", "boom", CompensationRecommendedAction.REPLAY,
                CompensationRefundSla.NONE, "retry_budget_exhausted_candidate_for_replay",
                CompensationHandlingStatus.MANUAL, Instant.now(), true
        );
        when(sagaCompensationApplicationService.recordManualFromDlt(any())).thenReturn(view);

        listener.onPaymentFailedDlt(
                message, acknowledgment, "com.example.Boom", "boom", "payment.failed", 1, 99L
        );

        ArgumentCaptor<RecordManualCompensationCommand> captor =
                ArgumentCaptor.forClass(RecordManualCompensationCommand.class);
        verify(sagaCompensationApplicationService).recordManualFromDlt(captor.capture());
        RecordManualCompensationCommand command = captor.getValue();
        assertThat(command.eventId()).isEqualTo(eventId);
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.currency()).isNull();
        assertThat(command.sourceTopic()).isEqualTo("payment.failed");
        assertThat(command.dltTopic()).isEqualTo("payment.failed.DLT");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("eventId/orderId 없으면 적재하지 않고 ack한다")
    void onPaymentFailedDlt_missingIds_skips() {
        PaymentFailedMessage message = new PaymentFailedMessage(
                UUID.randomUUID(), null, UUID.randomUUID(), null, "user-1", "10.00", "declined",
                Instant.now(), null, Instant.now()
        );

        listener.onPaymentFailedDlt(message, acknowledgment, null, null, null, null, null);

        verify(sagaCompensationApplicationService, never()).recordManualFromDlt(any());
        verify(acknowledgment).acknowledge();
    }
}
