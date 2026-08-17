package com.project.young.paymentservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentGatewayPort;
import com.project.young.paymentservice.application.port.output.PaymentGatewayResult;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentStateConflictException;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-13T08:03:10.343300Z");
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID OUTBOX_EVENT_ID = UUID.randomUUID();
    private static final String USER_ID = "user-1";
    private static final Money AMOUNT = new Money(new BigDecimal("25.00"));

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxPort paymentOutboxPort;

    @Mock
    private PaymentGatewayPort paymentGatewayPort;

    @Mock
    private IdGenerator idGenerator;

    @InjectMocks
    private PaymentApplicationService paymentApplicationService;

    @BeforeEach
    void setUp() {
        paymentApplicationService = new PaymentApplicationService(
                paymentRepository,
                paymentOutboxPort,
                paymentGatewayPort,
                idGenerator,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("processPayment: 신규 결제 성공 시 COMPLETED + outbox completed")
    void processPayment_whenNewAndGatewaySucceeds_completesAndEnqueues() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(PAYMENT_ID, OUTBOX_EVENT_ID);
        when(paymentGatewayPort.process(any(Payment.class))).thenReturn(PaymentGatewayResult.succeeded());
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(true);

        ProcessPaymentCommand command = new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD");

        Payment result = paymentApplicationService.processPayment(command);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository).insert(any(Payment.class));

        ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(paymentOutboxPort).enqueueCompleted(captor.capture());
        PaymentCompletedEvent event = captor.getValue();
        assertThat(event.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.occurredAt()).isEqualTo(FIXED_NOW);
        verify(paymentOutboxPort, never()).enqueueFailed(any());
    }

    @Test
    @DisplayName("processPayment: 신규 결제 실패 시 FAILED + outbox failed")
    void processPayment_whenNewAndGatewayFails_failsAndEnqueues() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(PAYMENT_ID, OUTBOX_EVENT_ID);
        when(paymentGatewayPort.process(any(Payment.class)))
                .thenReturn(PaymentGatewayResult.failed("card declined"));
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(true);

        Payment result = paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("card declined");

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentOutboxPort).enqueueFailed(captor.capture());
        assertThat(captor.getValue().failureReason()).isEqualTo("card declined");
        verify(paymentOutboxPort, never()).enqueueCompleted(any());
    }

    @Test
    @DisplayName("processPayment: 이미 COMPLETED면 idempotent하게 반환")
    void processPayment_whenAlreadyCompleted_isIdempotent() {
        Payment completed = Payment.reconstitute(
                new PaymentId(PAYMENT_ID),
                new OrderId(ORDER_ID),
                new UserId(USER_ID),
                AMOUNT,
                "USD",
                PaymentStatus.COMPLETED,
                null,
                FIXED_NOW,
                FIXED_NOW
        );
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.of(completed));

        Payment result = paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        );

        assertThat(result).isSameAs(completed);
        verify(paymentRepository, never()).insert(any());
        verify(paymentGatewayPort, never()).process(any());
        verify(paymentOutboxPort, never()).enqueueCompleted(any());
        verify(paymentOutboxPort, never()).enqueueFailed(any());
    }

    @Test
    @DisplayName("processPayment: PENDING 결제가 있으면 재개한다")
    void processPayment_whenPendingExists_resumesProcessing() {
        Payment pending = Payment.reconstitute(
                new PaymentId(PAYMENT_ID),
                new OrderId(ORDER_ID),
                new UserId(USER_ID),
                AMOUNT,
                "USD",
                PaymentStatus.PENDING,
                null,
                FIXED_NOW,
                FIXED_NOW
        );
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.of(pending));
        when(idGenerator.generateId()).thenReturn(OUTBOX_EVENT_ID);
        when(paymentGatewayPort.process(pending)).thenReturn(PaymentGatewayResult.succeeded());
        when(paymentRepository.updateStatus(pending, PaymentStatus.PENDING)).thenReturn(true);

        Payment result = paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepository, never()).insert(any());
        verify(paymentOutboxPort).enqueueCompleted(any());
    }

    @Test
    @DisplayName("processPayment: CAS 업데이트 실패 시 PaymentStateConflictException")
    void processPayment_whenStatusCasFails_throwsConflict() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(PAYMENT_ID);
        when(paymentGatewayPort.process(any(Payment.class))).thenReturn(PaymentGatewayResult.succeeded());
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(false);

        assertThatThrownBy(() -> paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        ))
                .isInstanceOf(PaymentStateConflictException.class);
    }
}
