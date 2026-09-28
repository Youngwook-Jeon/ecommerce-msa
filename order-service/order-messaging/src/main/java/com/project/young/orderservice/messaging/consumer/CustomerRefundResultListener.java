package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.CustomerRefundCompletedMessage;
import com.project.young.kafka.saga.dto.CustomerRefundFailedMessage;
import com.project.young.orderservice.application.dto.command.ApplyCustomerRefundResultCommand;
import com.project.young.orderservice.application.service.CustomerRefundApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "order-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CustomerRefundResultListener {

    private static final Logger log = LoggerFactory.getLogger(CustomerRefundResultListener.class);
    private final CustomerRefundApplicationService refunds;

    public CustomerRefundResultListener(CustomerRefundApplicationService refunds) {
        this.refunds = refunds;
    }

    @KafkaListener(topics = "${order-service.saga-events.customer-refund-completed-topic}",
            groupId = "${order-service.saga-events.customer-refund-completed-consumer-group}",
            containerFactory = "customerRefundCompletedKafkaListenerContainerFactory")
    public void onCompleted(CustomerRefundCompletedMessage message, Acknowledgment acknowledgment) {
        validate(message.refundId(), message.paymentId(), message.orderId(), message.userId());
        boolean applied = refunds.applyResult(new ApplyCustomerRefundResultCommand(message.refundId(),
                message.paymentId(), message.orderId(), message.userId(), true, null));
        acknowledgment.acknowledge();
        log.info("Customer refund completion consumed refundId={} eventId={} applied={}",
                message.refundId(), message.eventId(), applied);
    }

    @KafkaListener(topics = "${order-service.saga-events.customer-refund-failed-topic}",
            groupId = "${order-service.saga-events.customer-refund-failed-consumer-group}",
            containerFactory = "customerRefundFailedKafkaListenerContainerFactory")
    public void onFailed(CustomerRefundFailedMessage message, Acknowledgment acknowledgment) {
        validate(message.refundId(), message.paymentId(), message.orderId(), message.userId());
        boolean applied = refunds.applyResult(new ApplyCustomerRefundResultCommand(message.refundId(),
                message.paymentId(), message.orderId(), message.userId(), false, message.failureReason()));
        acknowledgment.acknowledge();
        log.info("Customer refund failure consumed refundId={} eventId={} applied={}",
                message.refundId(), message.eventId(), applied);
    }

    private static void validate(java.util.UUID refundId, java.util.UUID paymentId,
                                 java.util.UUID orderId, String userId) {
        if (refundId == null || paymentId == null || orderId == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Customer refund result has missing identifiers");
        }
    }
}
