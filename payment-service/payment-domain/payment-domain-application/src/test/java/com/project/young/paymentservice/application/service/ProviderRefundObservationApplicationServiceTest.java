package com.project.young.paymentservice.application.service;

import com.project.young.paymentservice.application.dto.command.ObserveProviderRefundCommand;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundResult;
import com.project.young.paymentservice.application.port.output.PaymentProviderPort.RefundState;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.UserId;
import com.project.young.paymentservice.domain.valueobject.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProviderRefundObservationApplicationServiceTest {
    private final PaymentRefundClaimPort claims = mock(PaymentRefundClaimPort.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentProviderPort provider = mock(PaymentProviderPort.class);
    private final PaymentRefundResultRecorder results = mock(PaymentRefundResultRecorder.class);
    private final RefundCompensationDltApplicationService operations = mock(RefundCompensationDltApplicationService.class);
    private final UUID paymentId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();
    private final Payment payment = mock(Payment.class);
    private ProviderRefundObservationApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ProviderRefundObservationApplicationService(claims, payments, provider, results, operations,
                "compensation-topic", "compensation-dlt");
    }

    @Test
    void unmatchedRefund_remainsInInbox() {
        assertThat(service.observe(command(RefundState.FAILED))).isFalse();
        verifyNoInteractions(payments, provider, results, operations);
    }

    @Test
    void failedWebhook_appliesEvenAfterSuccessWithoutAnotherMonetaryCall() {
        associated(PaymentRefundClaimPort.Kind.CUSTOMER);
        assertThat(service.observe(command(RefundState.FAILED))).isTrue();
        verify(results).recordCustomerObservation(any(), eq(new RefundResult("re_1", RefundState.FAILED)), eq("bank rejected"));
        verifyNoInteractions(provider);
    }

    @Test
    void staleSuccessWebhook_readsCurrentRefundAndCorrectsFailure() {
        associated(PaymentRefundClaimPort.Kind.CUSTOMER);
        when(provider.retrieveRefund("re_1")).thenReturn(new RefundResult("re_1", RefundState.FAILED));
        assertThat(service.observe(command(RefundState.SUCCEEDED))).isTrue();
        verify(results).recordCustomerObservation(any(), eq(new RefundResult("re_1", RefundState.FAILED)), any());
        verify(provider, never()).refund(any(), any());
    }

    @Test
    void differentProviderPayment_isRejected() {
        associated(PaymentRefundClaimPort.Kind.CUSTOMER);
        assertThatThrownBy(() -> service.observe(new ObserveProviderRefundCommand(
                "evt_1", "re_1", "pi_other", RefundState.FAILED, "bank rejected")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(provider, results, operations);
    }

    @Test
    void compensationFailure_usesSeparateCompensationQueue() {
        associated(PaymentRefundClaimPort.Kind.COMPENSATION);
        assertThat(service.observe(command(RefundState.FAILED))).isTrue();
        verify(operations).recordManualFollowUp(any());
        verifyNoInteractions(results, provider);
    }

    private ObserveProviderRefundCommand command(RefundState state) {
        return new ObserveProviderRefundCommand("evt_1", "re_1", "pi_1", state, "bank rejected");
    }

    private void associated(PaymentRefundClaimPort.Kind kind) {
        when(claims.findByProviderRefundId("re_1")).thenReturn(Optional.of(
                new PaymentRefundClaimPort.PendingRefund(paymentId, refundId, kind, "re_1")));
        when(payments.findById(new PaymentId(paymentId))).thenReturn(Optional.of(payment));
        when(payment.getProvider()).thenReturn(PaymentProvider.STRIPE);
        when(payment.getProviderPaymentId()).thenReturn("pi_1");
        when(payment.getOrderId()).thenReturn(new OrderId(UUID.randomUUID()));
        if (kind == PaymentRefundClaimPort.Kind.CUSTOMER) {
            when(payment.getUserId()).thenReturn(new UserId("user-1"));
        }
    }
}
