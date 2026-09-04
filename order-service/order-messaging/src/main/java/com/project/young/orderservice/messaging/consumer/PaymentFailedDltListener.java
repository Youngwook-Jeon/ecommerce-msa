package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentFailedMessage;
import com.project.young.orderservice.application.dto.compensation.RecordManualCompensationCommand;
import com.project.young.orderservice.application.dto.compensation.SagaCompensationView;
import com.project.young.orderservice.application.service.SagaCompensationApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code payment.failed.DLT} and durably records a manual follow-up for a
 * cancellation or inventory-release saga step that exhausted its retry budget.
 */
@Component
@ConditionalOnProperty(
        prefix = "order-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentFailedDltListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedDltListener.class);

    private final SagaCompensationApplicationService sagaCompensationApplicationService;
    private final String paymentFailedTopic;
    private final String paymentFailedDltTopic;

    public PaymentFailedDltListener(
            SagaCompensationApplicationService sagaCompensationApplicationService,
            @Value("${order-service.saga-events.payment-failed-topic}") String paymentFailedTopic,
            @Value("${order-service.saga-events.payment-failed-dlt-topic}") String paymentFailedDltTopic
    ) {
        this.sagaCompensationApplicationService = sagaCompensationApplicationService;
        this.paymentFailedTopic = paymentFailedTopic;
        this.paymentFailedDltTopic = paymentFailedDltTopic;
    }

    @KafkaListener(
            topics = "${order-service.saga-events.payment-failed-dlt-topic}",
            groupId = "${order-service.saga-events.payment-failed-dlt-consumer-group}",
            containerFactory = "paymentFailedDltKafkaListenerContainerFactory"
    )
    public void onPaymentFailedDlt(
            PaymentFailedMessage message,
            Acknowledgment acknowledgment,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionFqcn,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset
    ) {
        if (message == null || message.eventId() == null || message.orderId() == null) {
            log.warn(
                    "Skipping payment.failed.DLT message with missing eventId/orderId (exception={})",
                    exceptionFqcn
            );
            acknowledgment.acknowledge();
            return;
        }

        SagaCompensationView recorded = sagaCompensationApplicationService.recordManualFromDlt(
                new RecordManualCompensationCommand(
                        message.eventId(),
                        message.paymentId(),
                        message.orderId(),
                        message.userId(),
                        message.amount(),
                        null,
                        originalTopic == null || originalTopic.isBlank() ? paymentFailedTopic : originalTopic,
                        paymentFailedDltTopic,
                        originalPartition,
                        originalOffset,
                        exceptionFqcn,
                        exceptionMessage
                )
        );
        acknowledgment.acknowledge();

        log.info(
                "Acked payment.failed.DLT eventId={} orderId={} compensationId={} newlyCreated={}",
                recorded.eventId(),
                recorded.orderId(),
                recorded.id(),
                recorded.newlyCreated()
        );
    }
}
