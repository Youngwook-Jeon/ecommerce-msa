package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentCompletedMessage;
import com.project.young.orderservice.application.service.OrderApplicationService;
import com.project.young.orderservice.domain.entity.Order;
import com.project.young.orderservice.domain.valueobject.OrderId;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Debezium JSON from {@code payment.completed} and confirms the order saga.
 */
@Component
@ConditionalOnProperty(
        prefix = "order-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedListener.class);

    private final OrderApplicationService orderApplicationService;

    public PaymentCompletedListener(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @KafkaListener(
            topics = "${order-service.saga-events.payment-completed-topic}",
            groupId = "${order-service.saga-events.payment-completed-consumer-group}",
            containerFactory = "paymentCompletedKafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(PaymentCompletedMessage message) {
        if (message == null || message.orderId() == null) {
            log.warn("Skipping payment.completed message with missing orderId");
            return;
        }
        if (message.userId() == null || message.userId().isBlank()) {
            log.warn("Skipping payment.completed message with missing userId for order {}", message.orderId());
            return;
        }

        log.info(
                "Confirming order {} after payment {} (eventId={})",
                message.orderId(),
                message.paymentId(),
                message.eventId()
        );

        Order order = orderApplicationService.confirmPayment(
                new UserId(message.userId()),
                new OrderId(message.orderId())
        );

        log.info(
                "Order {} confirmed after payment {} (status={})",
                order.getId().getValue(),
                message.paymentId(),
                order.getStatus()
        );
    }
}
