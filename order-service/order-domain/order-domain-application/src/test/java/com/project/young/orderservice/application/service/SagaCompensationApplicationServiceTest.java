package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationDecision;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.port.output.CompensationObservationPort;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaCompensationApplicationServiceTest {

    @Mock
    private SagaCompensationPort sagaCompensationPort;

    @Mock
    private CompensationObservationPort compensationObservationPort;

    @InjectMocks
    private SagaCompensationApplicationService service;

    @Test
    @DisplayName("신규 DLT → MANUAL 적재 + 관측 발행")
    void recordManualFromDlt_insertsAndObserves() {
        RecordManualCompensationCommand command = sampleCommand(
                InventoryReservationConflictException.class.getName(),
                "conflict"
        );
        SagaCompensationView saved = sampleView(command, true);

        when(sagaCompensationPort.findByEventId(command.eventId())).thenReturn(Optional.empty());
        when(sagaCompensationPort.insertManual(
                eq(command),
                any(CompensationDecision.class),
                eq(CompensationHandlingStatus.MANUAL)
        )).thenReturn(saved);

        SagaCompensationView result = service.recordManualFromDlt(command);

        assertThat(result).isSameAs(saved);
        ArgumentCaptor<CompensationDecision> decisionCaptor = ArgumentCaptor.forClass(CompensationDecision.class);
        verify(sagaCompensationPort).insertManual(
                eq(command),
                decisionCaptor.capture(),
                eq(CompensationHandlingStatus.MANUAL)
        );
        assertThat(decisionCaptor.getValue().recommendedAction()).isEqualTo(CompensationRecommendedAction.REFUND);
        assertThat(decisionCaptor.getValue().refundSla()).isEqualTo(CompensationRefundSla.IMMEDIATE);
        verify(compensationObservationPort).recordManualCompensation(saved);
    }

    @Test
    @DisplayName("동일 eventId 중복이면 저장/관측을 건너뛴다")
    void recordManualFromDlt_duplicate_skips() {
        RecordManualCompensationCommand command = sampleCommand("x.Y", "msg");
        SagaCompensationView existing = sampleView(command, false);
        when(sagaCompensationPort.findByEventId(command.eventId())).thenReturn(Optional.of(existing));

        SagaCompensationView result = service.recordManualFromDlt(command);

        assertThat(result).isSameAs(existing);
        verify(sagaCompensationPort, never()).insertManual(any(), any(), any());
        verify(compensationObservationPort, never()).recordManualCompensation(any());
    }

    private static RecordManualCompensationCommand sampleCommand(String ex, String msg) {
        return new RecordManualCompensationCommand(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user-1",
                "10.00",
                "USD",
                "payment.completed",
                "payment.completed.DLT",
                0,
                42L,
                ex,
                msg
        );
    }

    private static SagaCompensationView sampleView(RecordManualCompensationCommand command, boolean newlyCreated) {
        return new SagaCompensationView(
                UUID.randomUUID(),
                command.eventId(),
                command.paymentId(),
                command.orderId(),
                command.userId(),
                command.amount(),
                command.currency(),
                command.sourceTopic(),
                command.dltTopic(),
                command.sourcePartition(),
                command.sourceOffset(),
                command.failureExceptionClass(),
                command.failureMessage(),
                CompensationRecommendedAction.REFUND,
                CompensationRefundSla.IMMEDIATE,
                "inventory_unavailable_or_expired_refund_only",
                CompensationHandlingStatus.MANUAL,
                Instant.parse("2026-08-23T12:00:00Z"),
                newlyCreated
        );
    }
}
