package com.project.young.paymentservice.messaging.consumer;

import org.assertj.core.api.Assertions;
import org.mockito.Mockito;
import org.springframework.kafka.support.Acknowledgment;

import com.project.young.kafka.saga.dto.CustomerRefundRequestedMessage;
import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRefundRequestedListenerTest {

    @Mock
    private PaymentApplicationService paymentApplicationService;

    @Test
    void onCustomerRefundRequested_usesCustomerRefundProcessingPath() {
        CustomerRefundRequestedListener listener = new CustomerRefundRequestedListener(paymentApplicationService);
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        CustomerRefundRequestedMessage message = new CustomerRefundRequestedMessage(
                UUID.randomUUID(), refundId, paymentId, orderId, "user-1", "duplicate charge", Instant.now(), Instant.now());
        when(paymentApplicationService.refundCustomerPayment(any(RefundCustomerPaymentCommand.class))).thenReturn(true);

        listener.onCustomerRefundRequested(message, null);

        ArgumentCaptor<RefundCustomerPaymentCommand> command = ArgumentCaptor.forClass(RefundCustomerPaymentCommand.class);
        verify(paymentApplicationService).refundCustomerPayment(command.capture());
        assertThat(command.getValue()).isEqualTo(new RefundCustomerPaymentCommand(refundId, paymentId, orderId, "user-1"));
        verify(paymentApplicationService, never()).refundPayment(any(RefundPaymentCommand.class));
    }

    @Test
    void invalidRequest_throwsWithoutAcknowledgingSoDltCanPreserveIt() {
        var ack = Mockito.mock(Acknowledgment.class);
        Assertions.assertThatThrownBy(() ->
                new CustomerRefundRequestedListener(paymentApplicationService).onCustomerRefundRequested(null, ack))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ack, never()).acknowledge();
        Mockito.verifyNoInteractions(paymentApplicationService);
    }
}
