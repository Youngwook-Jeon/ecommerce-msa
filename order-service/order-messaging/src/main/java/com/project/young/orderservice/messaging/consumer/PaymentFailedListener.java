package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentFailedMessage;
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
 * Consumes Debezium JSON from {@code payment.failed} and cancels the order saga.
 */
@Component
@ConditionalOnProperty(
        prefix = "order-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentFailedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedListener.class);

    private final OrderApplicationService orderApplicationService;

    public PaymentFailedListener(OrderApplicationService orderApplicationService) {
        this.orderApplicationService = orderApplicationService;
    }

    @KafkaListener(
            topics = "${order-service.saga-events.payment-failed-topic}",
            groupId = "${order-service.saga-events.payment-failed-consumer-group}",
            containerFactory = "paymentFailedKafkaListenerContainerFactory"
    )
    public void onPaymentFailed(PaymentFailedMessage message) {
        if (message == null || message.orderId() == null) {
            log.warn("Skipping payment.failed message with missing orderId");
            return;
        }
        if (message.userId() == null || message.userId().isBlank()) {
            log.warn("Skipping payment.failed message with missing userId for order {}", message.orderId());
            return;
        }

        log.info(
                "Cancelling order {} after payment {} failed (eventId={}, reason={})",
                message.orderId(),
                message.paymentId(),
                message.eventId(),
                message.failureReason()
        );

        Order order = orderApplicationService.cancelOrder(
                new UserId(message.userId()),
                new OrderId(message.orderId())
        );

        log.info(
                "Order {} cancelled after payment {} failed (status={})",
                order.getId().getValue(),
                message.paymentId(),
                order.getStatus()
        );
    }
}
