package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.dto.event.CustomerRefundCompletedEvent;
import com.project.young.paymentservice.application.dto.event.CustomerRefundFailedEvent;
import com.project.young.paymentservice.application.port.output.CustomerRefundProcessingPort;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.application.port.output.RefundCompensationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundResultRecorderTest {

    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");

    @Mock
    private RefundCompensationPort compensationPort;

    @Mock
    private CustomerRefundProcessingPort customerPort;

    @Mock
    private PaymentOutboxPort outboxPort;

    @Mock
    private IdGenerator idGenerator;

    private PaymentRefundResultRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new PaymentRefundResultRecorder(compensationPort, customerPort, outboxPort,
                idGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordCustomerRefund_writesOutboxOnlyForNewResult() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        RefundCustomerPaymentCommand command = new RefundCustomerPaymentCommand(refundId, paymentId, orderId, "user-1");
        when(customerPort.recordProcessed(refundId, paymentId, orderId, "user-1")).thenReturn(true);
        when(idGenerator.generateId()).thenReturn(eventId);

        assertThat(recorder.recordCustomerRefund(command)).isTrue();

        ArgumentCaptor<CustomerRefundCompletedEvent> event = ArgumentCaptor.forClass(CustomerRefundCompletedEvent.class);
        verify(outboxPort).enqueueCustomerRefundCompleted(event.capture());
        assertThat(event.getValue().eventId()).isEqualTo(eventId);
        assertThat(event.getValue().refundId()).isEqualTo(refundId);
    }

    @Test
    void recordCustomerRefund_skipsOutboxForDuplicateResult() {
        RefundCustomerPaymentCommand command = new RefundCustomerPaymentCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "user-1");

        assertThat(recorder.recordCustomerRefund(command)).isFalse();

        verify(outboxPort, never()).enqueueCustomerRefundCompleted(any());
        verify(idGenerator, never()).generateId();
    }

    @Test
    void recordCompensation_usesValidatedPaymentOrderId() {
        RefundPaymentCommand command = new RefundPaymentCommand(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID actualOrderId = UUID.randomUUID();
        when(compensationPort.recordProcessed(command.compensationEventId(), command.paymentId(), actualOrderId))
                .thenReturn(true);

        assertThat(recorder.recordCompensation(command, actualOrderId)).isTrue();

        verify(compensationPort).recordProcessed(command.compensationEventId(), command.paymentId(), actualOrderId);
    }

    @Test
    void recordCustomerRefundFailed_writesDistinctFailureOutbox() {
        RefundCustomerPaymentCommand command = new RefundCustomerPaymentCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "user-1");
        UUID eventId = UUID.randomUUID();
        when(idGenerator.generateId()).thenReturn(eventId);
        when(outboxPort.enqueueCustomerRefundFailed(any(CustomerRefundFailedEvent.class))).thenReturn(true);

        assertThat(recorder.recordCustomerRefundFailed(command, "PSP refund failed")).isTrue();

        ArgumentCaptor<CustomerRefundFailedEvent> event = ArgumentCaptor.forClass(CustomerRefundFailedEvent.class);
        verify(outboxPort).enqueueCustomerRefundFailed(event.capture());
        assertThat(event.getValue().eventId()).isEqualTo(eventId);
        assertThat(event.getValue().refundId()).isEqualTo(command.refundId());
        verify(customerPort, never()).recordProcessed(any(), any(), any(), any());
    }
}
