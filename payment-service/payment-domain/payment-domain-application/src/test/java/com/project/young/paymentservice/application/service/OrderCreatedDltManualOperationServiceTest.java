package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.compensation.OrderCreatedDltStatus;
import com.project.young.paymentservice.application.dto.query.OrderCreatedDltOperationsView;
import com.project.young.paymentservice.application.exception.OrderCreatedDltStateConflictException;
import com.project.young.paymentservice.application.port.output.OrderCreatedDltPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreatedDltManualOperationServiceTest {

    @Test
    void replay_claimsEscalatedItemAndResolvesAfterIdempotentPaymentProcessing() {
        OrderCreatedDltPort queue = mock(OrderCreatedDltPort.class);
        PaymentApplicationService payments = mock(PaymentApplicationService.class);
        OrderCreatedDltOperationsView item = item(OrderCreatedDltStatus.ESCALATED);
        when(queue.findByEventId(item.eventId())).thenReturn(Optional.of(item));
        when(queue.claimForManualReplay(any(), any())).thenReturn(true);

        service(queue, payments).replay(item.eventId());

        verify(payments).processPayment(any());
        verify(queue).resolve(item.eventId());
    }

    @Test
    void replay_rejectsRecordThatCannotBeClaimed() {
        OrderCreatedDltPort queue = mock(OrderCreatedDltPort.class);
        OrderCreatedDltOperationsView item = item(OrderCreatedDltStatus.REPLAYING);
        when(queue.findByEventId(item.eventId())).thenReturn(Optional.of(item));
        when(queue.claimForManualReplay(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service(queue, mock(PaymentApplicationService.class)).replay(item.eventId()))
                .isInstanceOf(OrderCreatedDltStateConflictException.class);
    }

    @Test
    void resolve_persistsOperatorReasonForEligibleRecord() {
        OrderCreatedDltPort queue = mock(OrderCreatedDltPort.class);
        OrderCreatedDltOperationsView item = item(OrderCreatedDltStatus.MANUAL);
        when(queue.findByEventId(item.eventId())).thenReturn(Optional.of(item));
        when(queue.resolveManually(item.eventId(), "duplicate order was handled")).thenReturn(true);

        service(queue, mock(PaymentApplicationService.class)).resolve(item.eventId(), "duplicate order was handled");

        verify(queue).resolveManually(item.eventId(), "duplicate order was handled");
    }

    private static OrderCreatedDltManualOperationService service(
            OrderCreatedDltPort queue,
            PaymentApplicationService payments
    ) {
        return new OrderCreatedDltManualOperationService(
                queue, payments, Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC));
    }

    private static OrderCreatedDltOperationsView item(OrderCreatedDltStatus status) {
        return new OrderCreatedDltOperationsView(
                UUID.randomUUID(), UUID.randomUUID(), "user-1", "49.99", "USD", status, 5,
                "example.ProviderUnavailable", "provider unavailable", Instant.now(), null
        );
    }
}
