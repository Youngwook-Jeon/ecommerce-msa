package com.project.young.orderservice.dataaccess.adapter;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.orderservice.application.dto.event.OrderCreatedEvent;
import com.project.young.orderservice.dataaccess.entity.OrderOutboxEntity;
import com.project.young.orderservice.dataaccess.repository.OrderOutboxJpaRepository;
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
class OrderOutboxAdapterTest {

    @Mock
    private OrderOutboxJpaRepository orderOutboxJpaRepository;

    @InjectMocks
    private OrderOutboxAdapter orderOutboxAdapter;

    @Test
    @DisplayName("enqueueCreated: outbox 이벤트 필드를 entity에 매핑해 저장한다")
    void enqueueCreated_mapsEventFieldsAndSaves() {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-06-13T08:03:10.343300Z");
        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                orderId,
                "user-1",
                new Money(new BigDecimal("49.99")),
                "USD",
                occurredAt
        );

        orderOutboxAdapter.enqueueCreated(event);

        ArgumentCaptor<OrderOutboxEntity> captor = ArgumentCaptor.forClass(OrderOutboxEntity.class);
        verify(orderOutboxJpaRepository).save(captor.capture());

        OrderOutboxEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("49.99");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
        assertThat(saved.getPublishedAt()).isNull();
    }
}
