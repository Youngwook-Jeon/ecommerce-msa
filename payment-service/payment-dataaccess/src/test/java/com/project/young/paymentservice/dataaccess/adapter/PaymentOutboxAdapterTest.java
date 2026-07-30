package com.project.young.paymentservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.dataaccess.entity.PaymentOutboxEntity;
import com.project.young.paymentservice.dataaccess.repository.PaymentOutboxJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxAdapterTest {

    @Mock
    private PaymentOutboxJpaRepository paymentOutboxJpaRepository;

    @InjectMocks
    private PaymentOutboxAdapter paymentOutboxAdapter;

    @Test
    @DisplayName("enqueueCompleted: outbox 이벤트 필드를 entity에 매핑해 저장한다")
    void enqueueCompleted_mapsEventFieldsAndSaves() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-13T08:03:10.343300Z");
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                eventId,
                paymentId,
                orderId,
                "user-1",
                new Money(new BigDecimal("19.99")),
                "USD",
                occurredAt
        );

        paymentOutboxAdapter.enqueueCompleted(event);

        ArgumentCaptor<PaymentOutboxEntity> captor = ArgumentCaptor.forClass(PaymentOutboxEntity.class);
        verify(paymentOutboxJpaRepository).save(captor.capture());

        PaymentOutboxEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getPaymentId()).isEqualTo(paymentId);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(saved.getAmount()).isEqualByComparingTo("19.99");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getFailureReason()).isNull();
        assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(saved.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("enqueueFailed: 실패 이벤트를 outbox entity에 매핑해 저장한다")
    void enqueueFailed_mapsEventFieldsAndSaves() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent(
                eventId,
                paymentId,
                orderId,
                "user-1",
                new Money(new BigDecimal("9.50")),
                "insufficient funds",
                Instant.now()
        );

        paymentOutboxAdapter.enqueueFailed(event);

        ArgumentCaptor<PaymentOutboxEntity> captor = ArgumentCaptor.forClass(PaymentOutboxEntity.class);
        verify(paymentOutboxJpaRepository).save(captor.capture());

        PaymentOutboxEntity saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("PAYMENT_FAILED");
        assertThat(saved.getCurrency()).isNull();
        assertThat(saved.getFailureReason()).isEqualTo("insufficient funds");
    }
}
