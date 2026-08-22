package com.project.young.paymentservice.application.service;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.paymentservice.application.dto.command.ApplyProviderPaymentResultCommand;
import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.dto.event.PaymentCompletedEvent;
import com.project.young.paymentservice.application.dto.event.PaymentFailedEvent;
import com.project.young.paymentservice.application.dto.query.ClientSecretView;
import com.project.young.paymentservice.application.port.output.IdGenerator;
import com.project.young.paymentservice.application.port.output.PaymentOutboxPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.ProviderPaymentSession;
import com.project.young.paymentservice.application.port.output.ProviderEventIdempotencyPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import com.project.young.paymentservice.domain.exception.PaymentStateConflictException;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import com.project.young.paymentservice.domain.valueobject.PaymentStatus;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private static final String PROVIDER_PAYMENT_ID = "pi_123";
    private static final String EVENT_ID = "evt_123";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOutboxPort paymentOutboxPort;

    @Mock
    private PaymentProviderPort paymentProviderPort;

    @Mock
    private ProviderEventIdempotencyPort providerEventIdempotencyPort;

    @Mock
    private IdGenerator idGenerator;

    private PaymentApplicationService paymentApplicationService;

    @BeforeEach
    void setUp() {
        paymentApplicationService = new PaymentApplicationService(
                paymentRepository,
                paymentOutboxPort,
                paymentProviderPort,
                providerEventIdempotencyPort,
                idGenerator,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("processPayment: stub 즉시 성공이면 COMPLETED + outbox + clientSecret 저장")
    void processPayment_whenNewAndImmediateSuccess_completes() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(PAYMENT_ID, OUTBOX_EVENT_ID);
        when(paymentProviderPort.createPayment(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            return ProviderPaymentSession.immediateSuccess(
                    PaymentProvider.STUB,
                    "stub_pi_" + payment.getId().getValue(),
                    "stub_secret_" + payment.getId().getValue()
            );
        });
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(true);

        Payment result = paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getClientSecret()).startsWith("stub_secret_");
        assertThat(result.getProvider()).isEqualTo(PaymentProvider.STUB);
        verify(paymentRepository).insert(any(Payment.class));

        ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(paymentOutboxPort).enqueueCompleted(captor.capture());
        assertThat(captor.getValue().paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(captor.getValue().occurredAt()).isEqualTo(FIXED_NOW);
        verify(paymentOutboxPort, never()).enqueueFailed(any());
    }

    @Test
    @DisplayName("processPayment: stub 즉시 실패면 FAILED + outbox failed")
    void processPayment_whenImmediateFailure_fails() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(PAYMENT_ID, OUTBOX_EVENT_ID);
        when(paymentProviderPort.createPayment(any(Payment.class))).thenReturn(
                ProviderPaymentSession.immediateFailure(
                        PaymentProvider.STUB,
                        "stub_pi_x",
                        "stub_secret_x",
                        "card declined"
                )
        );
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(true);

        Payment result = paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("card declined");

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(paymentOutboxPort).enqueueFailed(captor.capture());
        assertThat(captor.getValue().failureReason()).isEqualTo("card declined");
    }

    @Test
    @DisplayName("processPayment: async 세션이면 PENDING을 유지하고 clientSecret만 저장한다")
    void processPayment_whenAsyncSession_staysPending() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(PAYMENT_ID);
        when(paymentProviderPort.createPayment(any(Payment.class))).thenReturn(
                ProviderPaymentSession.async(
                        PaymentProvider.STRIPE,
                        "pi_123",
                        "pi_123_secret_abc"
                )
        );

        Payment result = paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        );

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getClientSecret()).isEqualTo("pi_123_secret_abc");
        assertThat(result.getProviderPaymentId()).isEqualTo("pi_123");
        verify(paymentRepository).insert(any(Payment.class));
        verify(paymentRepository, never()).updateStatus(any(), any());
        verify(paymentOutboxPort, never()).enqueueCompleted(any());
        verify(paymentOutboxPort, never()).enqueueFailed(any());
    }

    @Test
    @DisplayName("processPayment: 이미 provider session이 있으면 재생성하지 않는다")
    void processPayment_whenExistingPendingWithSession_doesNotRecreate() {
        Payment pending = pendingStripePayment();
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.of(pending));

        Payment result = paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        );

        assertThat(result).isSameAs(pending);
        verify(paymentProviderPort, never()).createPayment(any());
        verify(paymentRepository, never()).updateProviderSession(any());
    }

    @Test
    @DisplayName("getClientSecretByOrderId: 저장된 clientSecret을 반환한다")
    void getClientSecretByOrderId_returnsSecret() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.of(pendingStripePayment()));

        ClientSecretView view = paymentApplicationService.getClientSecretByOrderId(ORDER_ID);

        assertThat(view.clientSecret()).isEqualTo("pi_123_secret_abc");
        assertThat(view.provider()).isEqualTo("STRIPE");
        assertThat(view.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("getClientSecretByOrderId: 결제 없으면 PaymentDomainException")
    void getClientSecretByOrderId_whenMissing_throws() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentApplicationService.getClientSecretByOrderId(ORDER_ID))
                .isInstanceOf(PaymentDomainException.class)
                .hasMessageContaining("not found");
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
        verify(paymentProviderPort, never()).createPayment(any());
    }

    @Test
    @DisplayName("processPayment: CAS 실패 시 PaymentStateConflictException")
    void processPayment_whenStatusCasFails_throwsConflict() {
        when(paymentRepository.findByOrderId(new OrderId(ORDER_ID))).thenReturn(Optional.empty());
        when(idGenerator.generateId()).thenReturn(PAYMENT_ID);
        when(paymentProviderPort.createPayment(any(Payment.class))).thenReturn(
                ProviderPaymentSession.immediateSuccess(PaymentProvider.STUB, "stub_pi", "stub_secret")
        );
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(false);

        assertThatThrownBy(() -> paymentApplicationService.processPayment(
                new ProcessPaymentCommand(ORDER_ID, USER_ID, AMOUNT, "USD")
        ))
                .isInstanceOf(PaymentStateConflictException.class);
    }

    @Test
    @DisplayName("applyProviderPaymentResult: 성공 웹훅이면 COMPLETED + outbox")
    void applyProviderPaymentResult_whenSuccess_completes() {
        Payment pending = pendingStripePayment();
        when(paymentRepository.findByProviderPaymentId("STRIPE", PROVIDER_PAYMENT_ID))
                .thenReturn(Optional.of(pending));
        when(providerEventIdempotencyPort.tryMarkProcessed(
                EVENT_ID, PAYMENT_ID, "STRIPE", "PAYMENT_SUCCEEDED"
        )).thenReturn(true);
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(true);
        when(idGenerator.generateId()).thenReturn(OUTBOX_EVENT_ID);

        boolean applied = paymentApplicationService.applyProviderPaymentResult(
                ApplyProviderPaymentResultCommand.succeeded(EVENT_ID, PaymentProvider.STRIPE, PROVIDER_PAYMENT_ID)
        );

        assertThat(applied).isTrue();
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentOutboxPort).enqueueCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    @DisplayName("applyProviderPaymentResult: 실패 웹훅이면 FAILED + outbox")
    void applyProviderPaymentResult_whenFailure_fails() {
        Payment pending = pendingStripePayment();
        when(paymentRepository.findByProviderPaymentId("STRIPE", PROVIDER_PAYMENT_ID))
                .thenReturn(Optional.of(pending));
        when(providerEventIdempotencyPort.tryMarkProcessed(
                EVENT_ID, PAYMENT_ID, "STRIPE", "PAYMENT_FAILED"
        )).thenReturn(true);
        when(paymentRepository.updateStatus(any(Payment.class), eq(PaymentStatus.PENDING))).thenReturn(true);
        when(idGenerator.generateId()).thenReturn(OUTBOX_EVENT_ID);

        boolean applied = paymentApplicationService.applyProviderPaymentResult(
                ApplyProviderPaymentResultCommand.failed(
                        EVENT_ID,
                        PaymentProvider.STRIPE,
                        PROVIDER_PAYMENT_ID,
                        "card_declined"
                )
        );

        assertThat(applied).isTrue();
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentOutboxPort).enqueueFailed(any(PaymentFailedEvent.class));
    }

    @Test
    @DisplayName("applyProviderPaymentResult: 중복 eventId면 스킵")
    void applyProviderPaymentResult_whenDuplicateEvent_skips() {
        Payment pending = pendingStripePayment();
        when(paymentRepository.findByProviderPaymentId("STRIPE", PROVIDER_PAYMENT_ID))
                .thenReturn(Optional.of(pending));
        when(providerEventIdempotencyPort.tryMarkProcessed(
                EVENT_ID, PAYMENT_ID, "STRIPE", "PAYMENT_SUCCEEDED"
        )).thenReturn(false);

        boolean applied = paymentApplicationService.applyProviderPaymentResult(
                ApplyProviderPaymentResultCommand.succeeded(EVENT_ID, PaymentProvider.STRIPE, PROVIDER_PAYMENT_ID)
        );

        assertThat(applied).isFalse();
        verify(paymentRepository, never()).updateStatus(any(), any());
        verify(paymentOutboxPort, never()).enqueueCompleted(any());
    }

    @Test
    @DisplayName("applyProviderPaymentResult: payment를 못 찾으면 false")
    void applyProviderPaymentResult_whenPaymentMissing_returnsFalse() {
        when(paymentRepository.findByProviderPaymentId("STRIPE", PROVIDER_PAYMENT_ID))
                .thenReturn(Optional.empty());

        boolean applied = paymentApplicationService.applyProviderPaymentResult(
                ApplyProviderPaymentResultCommand.succeeded(EVENT_ID, PaymentProvider.STRIPE, PROVIDER_PAYMENT_ID)
        );

        assertThat(applied).isFalse();
        verify(providerEventIdempotencyPort, never()).tryMarkProcessed(any(), any(), any(), any());
    }

    private static Payment pendingStripePayment() {
        return Payment.reconstitute(
                new PaymentId(PAYMENT_ID),
                new OrderId(ORDER_ID),
                new UserId(USER_ID),
                AMOUNT,
                "USD",
                PaymentStatus.PENDING,
                null,
                PaymentProvider.STRIPE,
                PROVIDER_PAYMENT_ID,
                "pi_123_secret_abc",
                FIXED_NOW,
                FIXED_NOW
        );
    }
}
