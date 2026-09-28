package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.CustomerRefundCompletedMessage;
import com.project.young.kafka.saga.dto.CustomerRefundFailedMessage;
import com.project.young.orderservice.application.service.CustomerRefundApplicationService;
import com.project.young.orderservice.messaging.mapper.CustomerRefundResultMessageMapper;
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
    private final CustomerRefundResultMessageMapper mapper;

    public CustomerRefundResultListener(CustomerRefundApplicationService refunds,
                                       CustomerRefundResultMessageMapper mapper) {
        this.refunds = refunds;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "${order-service.saga-events.customer-refund-completed-topic}",
            groupId = "${order-service.saga-events.customer-refund-completed-consumer-group}",
            containerFactory = "customerRefundCompletedKafkaListenerContainerFactory")
    public void onCompleted(CustomerRefundCompletedMessage message, Acknowledgment acknowledgment) {
        boolean applied = refunds.applyResult(mapper.toCommand(message));
        acknowledgment.acknowledge();
        log.info("Customer refund completion consumed refundId={} eventId={} applied={}",
                message.refundId(), message.eventId(), applied);
    }

    @KafkaListener(topics = "${order-service.saga-events.customer-refund-failed-topic}",
            groupId = "${order-service.saga-events.customer-refund-failed-consumer-group}",
            containerFactory = "customerRefundFailedKafkaListenerContainerFactory")
    public void onFailed(CustomerRefundFailedMessage message, Acknowledgment acknowledgment) {
        boolean applied = refunds.applyResult(mapper.toCommand(message));
        acknowledgment.acknowledge();
        log.info("Customer refund failure consumed refundId={} eventId={} applied={}",
                message.refundId(), message.eventId(), applied);
    }

}
