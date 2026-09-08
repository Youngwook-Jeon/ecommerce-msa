package com.project.young.paymentservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentRefundRequestedMessage;
import com.project.young.paymentservice.application.dto.command.RecordRefundCompensationDltCommand;
import com.project.young.paymentservice.application.service.RefundCompensationDltApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(prefix = "payment-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentRefundRequestedDltListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundRequestedDltListener.class);
    private final RefundCompensationDltApplicationService service;
    private final String sourceTopic;
    private final String dltTopic;

    public PaymentRefundRequestedDltListener(RefundCompensationDltApplicationService service,
            @Value("${payment-service.saga-events.refund-requested-topic}") String sourceTopic,
            @Value("${payment-service.saga-events.refund-requested-dlt-topic}") String dltTopic) {
        this.service = service; this.sourceTopic = sourceTopic; this.dltTopic = dltTopic;
    }

    @KafkaListener(topics = "${payment-service.saga-events.refund-requested-dlt-topic}",
            groupId = "${payment-service.saga-events.refund-requested-dlt-consumer-group}",
            containerFactory = "paymentRefundRequestedDltKafkaListenerContainerFactory")
    public void onDlt(PaymentRefundRequestedMessage message, Acknowledgment acknowledgment,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionClass,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long offset) {
        if (message == null || message.compensationEventId() == null || message.paymentId() == null || message.orderId() == null) {
            log.warn("Skipping refund DLT record with missing compensationEventId/paymentId/orderId"); acknowledgment.acknowledge(); return;
        }
        boolean created = service.recordManualFollowUp(new RecordRefundCompensationDltCommand(message.compensationEventId(),
                message.paymentId(), message.orderId(), originalTopic == null || originalTopic.isBlank() ? sourceTopic : originalTopic,
                dltTopic, partition, offset, exceptionClass, exceptionMessage));
        acknowledgment.acknowledge();
        log.info("Acked payment.refund.requested.DLT eventId={} paymentId={} newlyCreated={}",
                message.compensationEventId(), message.paymentId(), created);
    }
}
