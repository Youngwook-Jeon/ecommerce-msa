package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentCompletedMessage;
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
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedListenerTest {

    @Mock
    private OrderApplicationService orderApplicationService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private PaymentCompletedListener listener;

    @Test
    @DisplayName("payment.completed 메시지를 confirmPayment로 위임하고 ack한다")
    void onPaymentCompleted_confirmsPayment() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        PaymentCompletedMessage message = new PaymentCompletedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                paymentId,
                orderId,
                "user-1",
                "49.99",
                "USD",
                Instant.parse("2026-06-13T08:03:10.343300Z"),
                null,
                Instant.parse("2026-06-13T08:03:10.345273Z")
        );
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(new OrderId(orderId));
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(orderApplicationService.confirmPayment(new UserId("user-1"), new OrderId(orderId)))
                .thenReturn(order);

        listener.onPaymentCompleted(message, acknowledgment);

        verify(orderApplicationService).confirmPayment(new UserId("user-1"), new OrderId(orderId));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("orderId가 null이면 confirmPayment를 호출하지 않고 ack한다")
    void onPaymentCompleted_whenOrderIdNull_skips() {
        PaymentCompletedMessage message = new PaymentCompletedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "user-1",
                "10.00",
                "USD",
                Instant.now(),
                null,
                Instant.now()
        );

        listener.onPaymentCompleted(message, acknowledgment);

        verify(orderApplicationService, never()).confirmPayment(any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("userId가 blank이면 confirmPayment를 호출하지 않고 ack한다")
    void onPaymentCompleted_whenUserIdBlank_skips() {
        PaymentCompletedMessage message = new PaymentCompletedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "  ",
                "10.00",
                "USD",
                Instant.now(),
                null,
                Instant.now()
        );

        listener.onPaymentCompleted(message, acknowledgment);

        verify(orderApplicationService, never()).confirmPayment(any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("confirmPayment 실패 시 ack하지 않는다 (재시도/DLT용)")
    void onPaymentCompleted_whenConfirmFails_doesNotAck() {
        UUID orderId = UUID.randomUUID();
        PaymentCompletedMessage message = new PaymentCompletedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                "user-1",
                "10.00",
                "USD",
                Instant.now(),
                null,
                Instant.now()
        );
        when(orderApplicationService.confirmPayment(new UserId("user-1"), new OrderId(orderId)))
                .thenThrow(new IllegalStateException("inventory unavailable"));

        try {
            listener.onPaymentCompleted(message, acknowledgment);
        } catch (IllegalStateException ignored) {
            // expected — container error handler retries / DLT
        }

        verify(acknowledgment, never()).acknowledge();
    }
}
