package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentFailedMessage;
import com.project.young.orderservice.application.service.OrderApplicationService;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.OrderStatus;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentFailedListenerTest {

    @Mock
    private OrderApplicationService orderApplicationService;

    @InjectMocks
    private PaymentFailedListener listener;

    @Test
    @DisplayName("payment.failed 메시지를 cancelOrder로 위임한다")
    void onPaymentFailed_cancelsOrder() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentFailedMessage message = new PaymentFailedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                paymentId,
                orderId,
                "user-1",
                "49.99",
                "card declined",
                Instant.parse("2026-06-13T08:03:10.343300Z"),
                null,
                Instant.parse("2026-06-13T08:03:10.345273Z")
        );
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(new OrderId(orderId));
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(orderApplicationService.cancelOrder(new UserId("user-1"), new OrderId(orderId)))
                .thenReturn(order);

        listener.onPaymentFailed(message);

        verify(orderApplicationService).cancelOrder(new UserId("user-1"), new OrderId(orderId));
    }

    @Test
    @DisplayName("orderId가 null이면 cancelOrder를 호출하지 않는다")
    void onPaymentFailed_whenOrderIdNull_skips() {
        PaymentFailedMessage message = new PaymentFailedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "user-1",
                "10.00",
                "declined",
                Instant.now(),
                null,
                Instant.now()
        );

        listener.onPaymentFailed(message);

        verify(orderApplicationService, never()).cancelOrder(any(), any());
    }
}
