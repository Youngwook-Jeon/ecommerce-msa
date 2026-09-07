package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentRefundRequestedMessage;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
class PaymentRefundRequestedListenerTest {

    @Mock
    private PaymentApplicationService paymentApplicationService;

    @InjectMocks
    private PaymentRefundRequestedListener listener;

    @Test
    @DisplayName("payment.refund.requested를 RefundPaymentCommand로 변환한다")
    void onPaymentRefundRequested_refundsPayment() {
        UUID compensationEventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentRefundRequestedMessage message = new PaymentRefundRequestedMessage(
                UUID.randomUUID(),
                compensationEventId,
                paymentId,
                orderId,
                "user-1",
                "inventory_unavailable",
                Instant.now(),
                Instant.now()
        );
        when(paymentApplicationService.refundPayment(any(RefundPaymentCommand.class))).thenReturn(true);

        listener.onPaymentRefundRequested(message);

        ArgumentCaptor<RefundPaymentCommand> commandCaptor = ArgumentCaptor.forClass(RefundPaymentCommand.class);
        verify(paymentApplicationService).refundPayment(commandCaptor.capture());
        assertThat(commandCaptor.getValue())
                .extracting(
                        RefundPaymentCommand::compensationEventId,
                        RefundPaymentCommand::paymentId,
                        RefundPaymentCommand::orderId
                )
                .containsExactly(compensationEventId, paymentId, orderId);
    }

    @Test
    @DisplayName("필수 식별자가 없으면 환불을 호출하지 않는다")
    void onPaymentRefundRequested_whenRequiredIdentifierMissing_skips() {
        PaymentRefundRequestedMessage message = new PaymentRefundRequestedMessage(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user-1",
                "reason",
                Instant.now(),
                Instant.now()
        );

        listener.onPaymentRefundRequested(message);

        verify(paymentApplicationService, never()).refundPayment(any(RefundPaymentCommand.class));
    }
}
