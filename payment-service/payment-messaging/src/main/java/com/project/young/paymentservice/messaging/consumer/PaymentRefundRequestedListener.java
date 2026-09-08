package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentRefundRequestedMessage;
import com.project.young.paymentservice.application.dto.command.RefundPaymentCommand;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes saga refund compensations relayed from Order Service's transactional outbox.
 */
@Component
@ConditionalOnProperty(
        prefix = "payment-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentRefundRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundRequestedListener.class);

    private final PaymentApplicationService paymentApplicationService;

    public PaymentRefundRequestedListener(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    public void onPaymentRefundRequested(PaymentRefundRequestedMessage message) { onPaymentRefundRequested(message, null); }

    @KafkaListener(
            topics = "${payment-service.saga-events.refund-requested-topic}",
            groupId = "${payment-service.saga-events.refund-requested-consumer-group}",
            containerFactory = "paymentRefundRequestedKafkaListenerContainerFactory"
    )
    public void onPaymentRefundRequested(PaymentRefundRequestedMessage message, Acknowledgment acknowledgment) {
        if (message == null
                || message.compensationEventId() == null
                || message.paymentId() == null
                || message.orderId() == null) {
            log.warn("Skipping payment.refund.requested message with missing compensationEventId, paymentId, or orderId");
            acknowledge(acknowledgment); return;
        }

        log.info(
                "Processing payment refund request compensationEventId={} paymentId={} orderId={}",
                message.compensationEventId(),
                message.paymentId(),
                message.orderId()
        );
        boolean applied = paymentApplicationService.refundPayment(new RefundPaymentCommand(
                message.compensationEventId(),
                message.paymentId(),
                message.orderId()
        ));
        log.info(
                "Payment refund request finished compensationEventId={} paymentId={} applied={}",
                message.compensationEventId(),
                message.paymentId(),
                applied
        );
        acknowledge(acknowledgment);
    }

    private static void acknowledge(Acknowledgment acknowledgment) { if (acknowledgment != null) acknowledgment.acknowledge(); }
}
