package com.project.young.paymentservice.application.service;

import org.assertj.core.api.Assertions;

import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.dto.event.CustomerRefundFailedEvent;
import com.project.young.paymentservice.application.port.output.CustomerRefundProcessingPort;
import com.project.young.paymentservice.application.port.output.CustomerRefundObservationPort;
import com.project.young.paymentservice.application.port.output.CustomerRefundReviewPort;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.application.port.output.RefundCompensationPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundResult;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundResultRecorderTest {
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");
    @Mock private RefundCompensationPort compensationPort;
    @Mock private CustomerRefundProcessingPort customerPort;
    @Mock private PaymentOutboxPort outboxPort;
    @Mock private IdGenerator idGenerator;
    @Mock private CustomerRefundObservationPort observations;
    @Mock private CustomerRefundReviewPort reviews;
    private PaymentRefundResultRecorder recorder;
    private final RefundCustomerPaymentCommand command = new RefundCustomerPaymentCommand(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "user-1");

    @BeforeEach
    void setUp() {
        recorder = new PaymentRefundResultRecorder(compensationPort, customerPort, outboxPort,
                idGenerator, Clock.fixed(NOW, ZoneOffset.UTC), observations, reviews);
    }

    @Test
    void success_recordsHistoryAndOutboxTogether() {
        var result = observe(RefundState.SUCCEEDED, NOW, null);
        when(customerPort.recordProcessed(command.refundId(), command.paymentId(), command.orderId(), command.userId()))
                .thenReturn(true);
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

        assertThat(recorder.recordCustomerObservation(command, result, null)).isTrue();

        verify(outboxPort).enqueueCustomerRefundCompleted(argThat(e ->
                e.resultVersion() == 1 && NOW.equals(e.refundCompletedAt())));
    }

    @Test
    void duplicateSuccess_doesNotPublishTwice() {
        var result = observe(RefundState.SUCCEEDED, NOW, null);
        assertThat(recorder.recordCustomerObservation(command, result, null)).isFalse();
        verifyNoInteractions(outboxPort, idGenerator);
    }

    @Test
    void pending_doesNotCompleteOrFail() {
        var result = observe(RefundState.PENDING, null, null);
        assertThat(recorder.recordCustomerObservation(command, result, null)).isFalse();
        verifyNoInteractions(customerPort, outboxPort, reviews);
    }

    @Test
    void initialFailure_emitsFailureWithoutClaimingSuccess() {
        var result = observe(RefundState.FAILED, null, NOW);
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());
        recorder.recordCustomerObservation(command, result, "bank rejected");
        verify(outboxPort).enqueueCustomerRefundFailed(argThat(e -> e.resultVersion() == 2 && !e.failedAfterCompletion()));
        verifyNoInteractions(customerPort, reviews);
    }

    @Test
    void lateFailure_preservesSuccessAndPublishesCorrectionAndQueuesReview() {
        Instant completedAt = NOW.minusSeconds(60);
        var result = observe(RefundState.FAILED, completedAt, NOW);
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());

        assertThat(recorder.recordCustomerObservation(command, result, "bank rejected")).isFalse();

        var event = ArgumentCaptor.forClass(CustomerRefundFailedEvent.class);
        verify(outboxPort).enqueueCustomerRefundFailed(event.capture());
        assertThat(event.getValue().failedAfterCompletion()).isTrue();
        assertThat(event.getValue().refundCompletedAt()).isEqualTo(completedAt);
        assertThat(event.getValue().refundFailedAt()).isEqualTo(NOW);
        verify(reviews).recordConfirmedLateFailure(command.refundId(), command.paymentId(), "re_1", "bank rejected");
        verifyNoInteractions(customerPort);
    }

    @Test
    void staleSuccessAfterFailure_doesNotRecordOrPublish() {
        var result = new RefundResult("re_1", RefundState.SUCCEEDED);
        when(observations.record(command.paymentId(), command.refundId(), result, NOW)).thenReturn(Optional.empty());
        assertThat(recorder.recordCustomerObservation(command, result, null)).isFalse();
        verifyNoInteractions(customerPort, outboxPort, reviews, idGenerator);
    }

    @Test
    void resultPersistenceFailure_propagatesToRetry() {
        var result = observe(RefundState.FAILED, NOW.minusSeconds(60), NOW);
        when(idGenerator.generateId()).thenReturn(UUID.randomUUID());
        doThrow(new IllegalStateException("db unavailable")).when(reviews)
                .recordConfirmedLateFailure(any(), any(), any(), any());
        Assertions.assertThatThrownBy(() ->
                recorder.recordCustomerObservation(command, result, "bank rejected")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void compensation_usesValidatedOrderId() {
        var refund = new RefundPaymentCommand(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID orderId = UUID.randomUUID();
        when(compensationPort.recordProcessed(refund.compensationEventId(), refund.paymentId(), orderId)).thenReturn(true);
        assertThat(recorder.recordCompensation(refund, orderId)).isTrue();
    }

    private RefundResult observe(RefundState state, Instant succeededAt, Instant failedAt) {
        var result = new RefundResult("re_1", state);
        when(observations.record(command.paymentId(), command.refundId(), result, NOW))
                .thenReturn(Optional.of(new CustomerRefundObservationPort.Observation(succeededAt, failedAt)));
        return result;
    }
}
