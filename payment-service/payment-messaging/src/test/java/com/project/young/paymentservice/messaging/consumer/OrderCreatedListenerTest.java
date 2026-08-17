package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.OrderCreatedMessage;
import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;
import com.project.young.common.domain.valueobject.Money;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

    @Mock
    private PaymentApplicationService paymentApplicationService;

    @InjectMocks
    private OrderCreatedListener listener;

    @Test
    @DisplayName("order.created 메시지를 ProcessPaymentCommand로 변환해 processPayment를 호출한다")
    void onOrderCreated_processesPayment() {
        UUID orderId = UUID.randomUUID();
        OrderCreatedMessage message = new OrderCreatedMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                orderId,
                "user-1",
                "49.99",
                "USD",
                Instant.parse("2026-06-13T08:03:10.343300Z"),
                null,
                Instant.parse("2026-06-13T08:03:10.345273Z")
        );
        Payment payment = Payment.reconstitute(
                new PaymentId(UUID.randomUUID()),
                new OrderId(orderId),
                new UserId("user-1"),
                new Money(new BigDecimal("49.99")),
                "USD",
                PaymentStatus.COMPLETED,
                null,
                Instant.now(),
                Instant.now()
        );
        when(paymentApplicationService.processPayment(any(ProcessPaymentCommand.class))).thenReturn(payment);

        listener.onOrderCreated(message);

        ArgumentCaptor<ProcessPaymentCommand> captor = ArgumentCaptor.forClass(ProcessPaymentCommand.class);
        verify(paymentApplicationService).processPayment(captor.capture());
        ProcessPaymentCommand command = captor.getValue();
        assertThat(command.orderId()).isEqualTo(orderId);
        assertThat(command.userId()).isEqualTo("user-1");
        assertThat(command.amount().getAmount()).isEqualByComparingTo("49.99");
        assertThat(command.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("orderId가 null이면 processPayment를 호출하지 않는다")
    void onOrderCreated_whenOrderIdNull_skips() {
        OrderCreatedMessage message = new OrderCreatedMessage(
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

        listener.onOrderCreated(message);

        verify(paymentApplicationService, never()).processPayment(any());
    }
}
