package com.project.young.paymentservice.messaging.consumer;

import com.project.young.paymentservice.application.dto.command.RecordCustomerRefundDltCommand;
import com.project.young.paymentservice.application.service.CustomerRefundDltApplicationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "payment-service.saga-events", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CustomerRefundDltListener {
    private final CustomerRefundDltApplicationService queue;

    public CustomerRefundDltListener(CustomerRefundDltApplicationService queue) {
        this.queue = queue;
    }

    @KafkaListener(topics = {"${payment-service.saga-events.customer-refund-requested-dlt-topic:customer.refund.requested.DLT}"},
            groupId = "${payment-service.saga-events.customer-refund-dlt-consumer-group:payment-service-customer-refund-dlt}",
            containerFactory = "customerRefundDltKafkaListenerContainerFactory")
    public void onDlt(ConsumerRecord<String, String> record, Acknowledgment acknowledgment,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_TOPIC, required = false) String sourceTopic,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_PARTITION, required = false) Integer sourcePartition,
            @Header(name = KafkaHeaders.DLT_ORIGINAL_OFFSET, required = false) Long sourceOffset,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) String exceptionClass,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String exceptionMessage) {
        queue.record(new RecordCustomerRefundDltCommand(record.topic(), record.partition(), record.offset(),
                record.key(), record.value(), sourceTopic, sourcePartition, sourceOffset,
                exceptionClass, exceptionMessage));
        // A DB error propagates: the Kafka offset must never advance before durable storage.
        acknowledgment.acknowledge();
    }
}
