package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.OrderCreatedMessage;
import com.project.young.paymentservice.application.dto.command.RecordOrderCreatedDltCommand;
import com.project.young.paymentservice.application.service.OrderCreatedDltApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "payment-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderCreatedDltListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedDltListener.class);

    private final OrderCreatedDltApplicationService service;
    private final String sourceTopic;
    private final String dltTopic;

    public OrderCreatedDltListener(
            OrderCreatedDltApplicationService service,
            @Value("${payment-service.saga-events.order-created-topic}") String sourceTopic,
            @Value("${payment-service.saga-events.order-created-dlt-topic}") String dltTopic
    ) {
        this.service = service;
        this.sourceTopic = sourceTopic;
        this.dltTopic = dltTopic;
    }

    @KafkaListener(
            topics = "${payment-service.saga-events.order-created-dlt-topic}",
            groupId = "${payment-service.saga-events.order-created-dlt-consumer-group}",
            containerFactory = "orderCreatedDltKafkaListenerContainerFactory"
    )
    public void onDlt(
            OrderCreatedMessage message,
            Acknowledgment acknowledgment,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionClass,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long offset
    ) {
        if (message == null || message.eventId() == null || message.orderId() == null
                || message.userId() == null || message.userId().isBlank()
                || message.totalAmount() == null || message.totalAmount().isBlank()
                || message.currency() == null || message.currency().isBlank()) {
            log.warn("Skipping order.created.DLT message with missing required fields");
            acknowledgment.acknowledge();
            return;
        }

        boolean created = service.recordManualFollowUp(new RecordOrderCreatedDltCommand(
                message.eventId(), message.orderId(), message.userId(), message.totalAmount(), message.currency(),
                originalTopic == null || originalTopic.isBlank() ? sourceTopic : originalTopic,
                dltTopic, partition, offset, exceptionClass, exceptionMessage
        ));
        acknowledgment.acknowledge();
        log.info("Acked order.created.DLT eventId={} orderId={} newlyCreated={}",
                message.eventId(), message.orderId(), created);
    }
}
