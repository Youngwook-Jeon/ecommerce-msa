package com.project.young.paymentservice.messaging.error;

import com.project.young.paymentservice.domain.exception.PaymentRefundRejectedException;
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

/** Customer refunds retry uncertain PSP failures and preserve rejected requests in their own DLT. */
@Component
public class CustomerRefundKafkaFailureStrategy {

    private static final Logger log = LoggerFactory.getLogger(CustomerRefundKafkaFailureStrategy.class);

    private final PaymentSagaKafkaErrorProperties properties;
    private final KafkaTemplate<Object, Object> dltKafkaTemplate;

    public CustomerRefundKafkaFailureStrategy(
            PaymentSagaKafkaErrorProperties properties,
            KafkaTemplate<Object, Object> dltKafkaTemplate
    ) {
        this.properties = properties;
        this.dltKafkaTemplate = dltKafkaTemplate;
    }

    public void configure(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) -> {
                    String dltTopic = record.topic() + properties.dlt().topicSuffix();
                    log.error("Publishing customer refund to DLT topic={} partition={} offset={} cause={}",
                            dltTopic, record.partition(), record.offset(), exception.toString());
                    return new TopicPartition(dltTopic, record.partition());
                }
        );
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(properties.retry().backoffIntervalMs(), properties.retry().maxAttempts()));
        handler.setCommitRecovered(true);
        handler.addNotRetryableExceptions(PaymentRefundRejectedException.class);
        factory.setCommonErrorHandler(handler);
    }
}
