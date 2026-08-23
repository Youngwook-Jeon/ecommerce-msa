package com.project.young.orderservice.messaging.consumer;

import com.project.young.kafka.saga.dto.PaymentCompletedMessage;
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
 * Consumes {@code payment.completed.DLT}, classifies policy recommendation, and persists MANUAL.
 * Auto refund/replay is intentionally deferred; observation goes through a swappable strategy.
 */
@Component
@ConditionalOnProperty(
        prefix = "order-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentCompletedDltListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedDltListener.class);

    private final SagaCompensationApplicationService sagaCompensationApplicationService;
    private final String paymentCompletedTopic;
    private final String paymentCompletedDltTopic;

    public PaymentCompletedDltListener(
            SagaCompensationApplicationService sagaCompensationApplicationService,
            @Value("${order-service.saga-events.payment-completed-topic}") String paymentCompletedTopic,
            @Value("${order-service.saga-events.payment-completed-dlt-topic}") String paymentCompletedDltTopic
    ) {
        this.sagaCompensationApplicationService = sagaCompensationApplicationService;
        this.paymentCompletedTopic = paymentCompletedTopic;
        this.paymentCompletedDltTopic = paymentCompletedDltTopic;
    }

    @KafkaListener(
            topics = "${order-service.saga-events.payment-completed-dlt-topic}",
            groupId = "${order-service.saga-events.payment-completed-dlt-consumer-group}",
            containerFactory = "paymentCompletedDltKafkaListenerContainerFactory"
    )
    public void onPaymentCompletedDlt(
            PaymentCompletedMessage message,
            Acknowledgment acknowledgment,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionFqcn,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer originalPartition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long originalOffset
    ) {
        if (message == null || message.eventId() == null || message.orderId() == null) {
            log.warn(
                    "Skipping payment.completed.DLT message with missing eventId/orderId (exception={})",
                    exceptionFqcn
            );
            acknowledgment.acknowledge();
            return;
        }

        RecordManualCompensationCommand command = new RecordManualCompensationCommand(
                message.eventId(),
                message.paymentId(),
                message.orderId(),
                message.userId(),
                message.amount(),
                message.currency(),
                originalTopic == null || originalTopic.isBlank() ? paymentCompletedTopic : originalTopic,
                paymentCompletedDltTopic,
                originalPartition,
                originalOffset,
                exceptionFqcn,
                exceptionMessage
        );

        SagaCompensationView recorded = sagaCompensationApplicationService.recordManualFromDlt(command);
        acknowledgment.acknowledge();

        log.info(
                "Acked payment.completed.DLT eventId={} orderId={} compensationId={} newlyCreated={}",
                recorded.eventId(),
                recorded.orderId(),
                recorded.id(),
                recorded.newlyCreated()
        );
    }
}
