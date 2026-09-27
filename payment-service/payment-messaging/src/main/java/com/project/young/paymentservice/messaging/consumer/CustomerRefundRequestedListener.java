package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.CustomerRefundRequestedMessage;
import com.project.young.paymentservice.application.dto.command.RefundCustomerPaymentCommand;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Applies a customer refund request; refundId is also the PSP idempotency key. */
@Component
@ConditionalOnProperty(prefix = "payment-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CustomerRefundRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerRefundRequestedListener.class);
    private final PaymentApplicationService paymentApplicationService;

    public CustomerRefundRequestedListener(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @KafkaListener(
            topics = "${payment-service.saga-events.customer-refund-requested-topic}",
            groupId = "${payment-service.saga-events.customer-refund-requested-consumer-group}",
            containerFactory = "customerRefundRequestedKafkaListenerContainerFactory"
    )
    public void onCustomerRefundRequested(CustomerRefundRequestedMessage message, Acknowledgment acknowledgment) {
        if (message == null || message.refundId() == null || message.paymentId() == null || message.orderId() == null) {
            log.warn("Skipping customer.refund.requested with missing refundId, paymentId, or orderId");
            acknowledge(acknowledgment);
            return;
        }
        log.info("Processing customer refund refundId={} paymentId={} orderId={}",
                message.refundId(), message.paymentId(), message.orderId());
        paymentApplicationService.refundCustomerPayment(new RefundCustomerPaymentCommand(
                message.refundId(),
                message.paymentId(),
                message.orderId(),
                message.userId()
        ));
        acknowledge(acknowledgment);
        log.info("Completed customer refund refundId={} paymentId={}", message.refundId(), message.paymentId());
    }

    private static void acknowledge(Acknowledgment acknowledgment) {
        if (acknowledgment != null) {
            acknowledgment.acknowledge();
        }
    }
}
