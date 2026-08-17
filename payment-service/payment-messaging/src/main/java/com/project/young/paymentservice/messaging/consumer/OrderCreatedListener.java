package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.OrderCreatedMessage;
import com.project.young.paymentservice.application.dto.command.ProcessPaymentCommand;
import com.project.young.paymentservice.application.service.PaymentApplicationService;
import com.project.young.paymentservice.domain.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Debezium JSON from {@code order.created} and runs stub payment processing.
 */
@Component
@ConditionalOnProperty(
        prefix = "payment-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final PaymentApplicationService paymentApplicationService;

    public OrderCreatedListener(PaymentApplicationService paymentApplicationService) {
        this.paymentApplicationService = paymentApplicationService;
    }

    @KafkaListener(
            topics = "${payment-service.saga-events.order-created-topic}",
            groupId = "${payment-service.saga-events.order-created-consumer-group}",
            containerFactory = "orderCreatedKafkaListenerContainerFactory"
    )
    public void onOrderCreated(OrderCreatedMessage message) {
        if (message == null || message.orderId() == null) {
            log.warn("Skipping order.created message with missing orderId");
            return;
        }
        if (message.userId() == null || message.userId().isBlank()) {
            log.warn("Skipping order.created message with missing userId for order {}", message.orderId());
            return;
        }
        if (message.totalAmount() == null || message.totalAmount().isBlank()) {
            log.warn("Skipping order.created message with missing totalAmount for order {}", message.orderId());
            return;
        }

        log.info(
                "Processing payment for order {} (eventId={}, amount={} {})",
                message.orderId(),
                message.eventId(),
                message.totalAmount(),
                message.currency()
        );

        Payment payment = paymentApplicationService.processPayment(
                ProcessPaymentCommand.fromOrderCreated(
                        message.orderId(),
                        message.userId(),
                        message.totalAmount(),
                        message.currency()
                )
        );

        log.info(
                "Payment {} for order {} finished with status {}",
                payment.getId().getValue(),
                message.orderId(),
                payment.getStatus()
        );
    }
}
