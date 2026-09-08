package com.project.young.paymentservice.messaging.error;

import com.project.young.paymentservice.domain.exception.PaymentDomainException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

@Component
public class PaymentSagaKafkaFailureStrategy {
    private static final Logger log = LoggerFactory.getLogger(PaymentSagaKafkaFailureStrategy.class);
    private final PaymentSagaKafkaErrorProperties properties;
    private final KafkaTemplate<Object, Object> paymentSagaDltKafkaTemplate;

    public PaymentSagaKafkaFailureStrategy(PaymentSagaKafkaErrorProperties properties,
                                           KafkaTemplate<Object, Object> paymentSagaDltKafkaTemplate) {
        this.properties = properties;
        this.paymentSagaDltKafkaTemplate = paymentSagaDltKafkaTemplate;
    }

    public void configure(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(paymentSagaDltKafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) -> {
                    String dltTopic = record.topic() + properties.dlt().topicSuffix();
                    log.error("Publishing exhausted payment saga record to DLT topic={} partition={} offset={} cause={}",
                            dltTopic, record.partition(), record.offset(), exception.toString());
                    return new TopicPartition(dltTopic, record.partition());
                });
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retry().backoffIntervalMs(), properties.retry().maxAttempts()));
        errorHandler.setCommitRecovered(true);
        errorHandler.addNotRetryableExceptions(PaymentDomainException.class);
        factory.setCommonErrorHandler(errorHandler);
    }
}
