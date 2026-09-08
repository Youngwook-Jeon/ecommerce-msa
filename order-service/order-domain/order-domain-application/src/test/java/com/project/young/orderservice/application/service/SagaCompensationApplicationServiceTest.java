package com.project.young.orderservice.application.service;

import com.project.young.orderservice.application.compensation.CompensationDecision;
import com.project.young.orderservice.application.compensation.CompensationHandlingStatus;
import com.project.young.orderservice.application.compensation.CompensationRecommendedAction;
import com.project.young.orderservice.application.compensation.CompensationRefundSla;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.dto.event.RefundRequestedEvent;
import com.project.young.orderservice.application.dto.event.InventoryReleaseRequestedEvent;
import com.project.young.orderservice.application.port.output.CompensationObservationPort;
import com.project.young.orderservice.application.port.output.InventoryReservationConflictException;
import com.project.young.orderservice.application.port.output.InventoryReleaseRequestedOutboxPort;
import com.project.young.orderservice.application.port.output.RefundRequestedOutboxPort;
import com.project.young.orderservice.application.port.output.SagaCompensationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private RefundRequestedOutboxPort refundRequestedOutboxPort;

    @Mock
    private InventoryReleaseRequestedOutboxPort inventoryReleaseRequestedOutboxPort;

    private SagaCompensationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SagaCompensationApplicationService(
                sagaCompensationPort,
                compensationObservationPort,
                refundRequestedOutboxPort,
                inventoryReleaseRequestedOutboxPort,
                "payment.failed"
        );
    }

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
        ArgumentCaptor<RefundRequestedEvent> refundEventCaptor = ArgumentCaptor.forClass(RefundRequestedEvent.class);
        verify(refundRequestedOutboxPort).enqueue(refundEventCaptor.capture());
        assertThat(refundEventCaptor.getValue())
                .extracting(
                        RefundRequestedEvent::compensationEventId,
                        RefundRequestedEvent::paymentId,
                        RefundRequestedEvent::orderId,
                        RefundRequestedEvent::userId,
                        RefundRequestedEvent::reason
                )
                .containsExactly(
                        saved.eventId(),
                        saved.paymentId(),
                        saved.orderId(),
                        saved.userId(),
                        saved.classificationReason()
                );
        verify(compensationObservationPort).recordManualCompensation(saved);
    }

    @Test
    @DisplayName("payment.failed DLT는 refund 대신 inventory release 이벤트를 적재한다")
    void recordManualFromPaymentFailedDlt_enqueuesInventoryReleaseOnly() {
        RecordManualCompensationCommand command = new RecordManualCompensationCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "user-1", "10.00", null,
                "payment.failed", "payment.failed.DLT", 0, 42L,
                InventoryReservationConflictException.class.getName(), "release failed"
        );
        SagaCompensationView saved = new SagaCompensationView(
                UUID.randomUUID(), command.eventId(), command.paymentId(), command.orderId(), command.userId(),
                command.amount(), command.currency(), command.sourceTopic(), command.dltTopic(),
                command.sourcePartition(), command.sourceOffset(), command.failureExceptionClass(), command.failureMessage(),
                CompensationRecommendedAction.RELEASE_INVENTORY, CompensationRefundSla.NONE,
                "payment_failed_inventory_release_required", CompensationHandlingStatus.MANUAL, Instant.now(), true
        );
        when(sagaCompensationPort.findByEventId(command.eventId())).thenReturn(Optional.empty());
        when(sagaCompensationPort.insertManual(eq(command), any(CompensationDecision.class), eq(CompensationHandlingStatus.MANUAL)))
                .thenReturn(saved);

        service.recordManualFromDlt(command);

        ArgumentCaptor<CompensationDecision> decisionCaptor = ArgumentCaptor.forClass(CompensationDecision.class);
        verify(sagaCompensationPort).insertManual(
                eq(command),
                decisionCaptor.capture(),
                eq(CompensationHandlingStatus.MANUAL)
        );
        assertThat(decisionCaptor.getValue().recommendedAction())
                .isEqualTo(CompensationRecommendedAction.RELEASE_INVENTORY);
        verify(refundRequestedOutboxPort, never()).enqueue(any());
        ArgumentCaptor<InventoryReleaseRequestedEvent> releaseCaptor = ArgumentCaptor.forClass(InventoryReleaseRequestedEvent.class);
        verify(inventoryReleaseRequestedOutboxPort).enqueue(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().compensationEventId()).isEqualTo(command.eventId());
        assertThat(releaseCaptor.getValue().orderId()).isEqualTo(command.orderId());
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
        verify(refundRequestedOutboxPort, never()).enqueue(any());
        verify(inventoryReleaseRequestedOutboxPort, never()).enqueue(any());
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
