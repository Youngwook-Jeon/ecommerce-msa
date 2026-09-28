package com.project.young.paymentservice.application.service;

import org.assertj.core.api.Assertions;

import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.port.output.PaymentRefundClaimPort;
import com.project.young.paymentservice.domain.entity.Payment;
import com.project.young.paymentservice.domain.exception.PaymentRefundRejectedException;
import com.project.young.paymentservice.domain.exception.PaymentRefundNeedsReviewException;
import com.project.young.paymentservice.domain.exception.PaymentRefundUnavailableException;
import com.project.young.paymentservice.application.dto.command.EscalateCustomerRefundCommand;
import com.project.young.paymentservice.domain.repository.PaymentRepository;
import com.project.young.paymentservice.domain.valueobject.OrderId;
import com.project.young.paymentservice.domain.valueobject.PaymentId;
import com.project.young.paymentservice.domain.valueobject.UserId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

class ProviderRefundReconciliationExecutorTest {

    @Test
    void uncertainCustomerOutcome_isEscalatedWithoutUsingCompensationQueue() {
        CustomerFixture fixture = new CustomerFixture();
        doThrow(new PaymentRefundNeedsReviewException("Retry window expired"))
                .when(fixture.application).refundCustomerPayment(fixture.command);

        fixture.executor.reconcileRefunds();

        verify(fixture.customerReviews).escalate(new EscalateCustomerRefundCommand(
                fixture.command.refundId(), fixture.command.paymentId(), "re_pending",
                PaymentRefundNeedsReviewException.class.getName(), "Retry window expired"));
        verifyNoInteractions(fixture.compensationReviews);
    }

    @Test
    void transientCustomerFailure_isNotEscalated() {
        CustomerFixture fixture = new CustomerFixture();
        doThrow(new PaymentRefundUnavailableException("PSP unavailable", new RuntimeException()))
                .when(fixture.application).refundCustomerPayment(fixture.command);

        fixture.executor.reconcileRefunds();

        verifyNoInteractions(fixture.customerReviews, fixture.compensationReviews);
    }

    @Test
    void reviewPersistenceFailure_doesNotEscapeBatch() {
        CustomerFixture fixture = new CustomerFixture();
        doThrow(new PaymentRefundRejectedException("Review required"))
                .when(fixture.application).refundCustomerPayment(fixture.command);
        doThrow(new IllegalStateException("DB unavailable")).when(fixture.customerReviews).escalate(any());

        Assertions.assertThatCode(fixture.executor::reconcileRefunds)
                .doesNotThrowAnyException();
        verify(fixture.customerReviews).escalate(any());
    }

    private static class CustomerFixture {
        final PaymentRefundClaimPort claims = mock(PaymentRefundClaimPort.class);
        final PaymentRepository payments = mock(PaymentRepository.class);
        final PaymentApplicationService application = mock(PaymentApplicationService.class);
        final RefundCompensationDltApplicationService compensationReviews = mock(RefundCompensationDltApplicationService.class);
        final CustomerRefundReviewApplicationService customerReviews = mock(CustomerRefundReviewApplicationService.class);
        final RefundCustomerPaymentCommand command = new RefundCustomerPaymentCommand(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "user-1");
        final ProviderRefundReconciliationExecutor executor = new ProviderRefundReconciliationExecutor(
                claims, payments, application, compensationReviews, customerReviews,
                "payment.refund.requested", "payment.refund.requested.DLT");

        CustomerFixture() {
            Payment payment = mock(Payment.class);
            when(claims.findUnfinalized(100)).thenReturn(List.of(new PaymentRefundClaimPort.PendingRefund(
                    command.paymentId(), command.refundId(), PaymentRefundClaimPort.Kind.CUSTOMER, "re_pending")));
            when(payments.findById(new PaymentId(command.paymentId()))).thenReturn(Optional.of(payment));
            when(payment.getOrderId()).thenReturn(new OrderId(command.orderId()));
            when(payment.getUserId()).thenReturn(new UserId(command.userId()));
        }
    }

    @Test
    void pendingCustomerRefund_isRecheckedWithoutCreatingAnotherRequestDirectly() {
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentRefundClaimPort claims = mock(PaymentRefundClaimPort.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentApplicationService applicationService = mock(PaymentApplicationService.class);
        Payment payment = mock(Payment.class);
        when(claims.findUnfinalized(100)).thenReturn(List.of(new PaymentRefundClaimPort.PendingRefund(
                paymentId, refundId, PaymentRefundClaimPort.Kind.CUSTOMER, "re_pending")));
        when(payments.findById(new PaymentId(paymentId))).thenReturn(Optional.of(payment));
        when(payment.getOrderId()).thenReturn(new OrderId(orderId));
        when(payment.getUserId()).thenReturn(new UserId("user-1"));

        new ProviderRefundReconciliationExecutor(claims, payments, applicationService,
                mock(RefundCompensationDltApplicationService.class), mock(CustomerRefundReviewApplicationService.class), "payment.refund.requested",
                "payment.refund.requested.DLT").reconcileRefunds();

        verify(applicationService).refundCustomerPayment(new RefundCustomerPaymentCommand(
                refundId, paymentId, orderId, "user-1"));
    }

    @Test
    void failedCompensationRefund_isQueuedForManualReview() {
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentRefundClaimPort claims = mock(PaymentRefundClaimPort.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentApplicationService applicationService = mock(PaymentApplicationService.class);
        RefundCompensationDltApplicationService operations = mock(RefundCompensationDltApplicationService.class);
        Payment payment = mock(Payment.class);
        when(claims.findUnfinalized(100)).thenReturn(List.of(new PaymentRefundClaimPort.PendingRefund(
                paymentId, requestId, PaymentRefundClaimPort.Kind.COMPENSATION, "re_failed")));
        when(payments.findById(new PaymentId(paymentId))).thenReturn(Optional.of(payment));
        when(payment.getOrderId()).thenReturn(new OrderId(orderId));
        doThrow(new PaymentRefundRejectedException("PSP refund failed"))
                .when(applicationService).refundPayment(new RefundPaymentCommand(requestId, paymentId, orderId));

        new ProviderRefundReconciliationExecutor(claims, payments, applicationService, operations,
                mock(CustomerRefundReviewApplicationService.class), "payment.refund.requested", "payment.refund.requested.DLT").reconcileRefunds();

        ArgumentCaptor<RecordRefundCompensationDltCommand> command =
                ArgumentCaptor.forClass(RecordRefundCompensationDltCommand.class);
        verify(operations).recordManualFollowUp(command.capture());
        assertThat(command.getValue().compensationEventId()).isEqualTo(requestId);
        assertThat(command.getValue().paymentId()).isEqualTo(paymentId);
    }
}
