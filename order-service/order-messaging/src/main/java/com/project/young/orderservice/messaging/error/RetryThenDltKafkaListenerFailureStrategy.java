package com.project.young.orderservice.messaging.error;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manual ack + fixed retry budget, then publish the failed record to {@code <topic><suffix>}.
 */
public final class RetryThenDltKafkaListenerFailureStrategy implements KafkaListenerFailureStrategy {

    private static final Logger log = LoggerFactory.getLogger(RetryThenDltKafkaListenerFailureStrategy.class);

    private final SagaKafkaConsumerErrorProperties properties;
    private final KafkaTemplate<Object, Object> dltKafkaTemplate;

    public RetryThenDltKafkaListenerFailureStrategy(
            SagaKafkaConsumerErrorProperties properties,
            KafkaTemplate<Object, Object> dltKafkaTemplate
    ) {
        this.properties = properties;
        this.dltKafkaTemplate = dltKafkaTemplate;
    }

    @Override
    public void configure(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(createErrorHandler());
    }

    CommonErrorHandler createErrorHandler() {
        long maxAttempts = properties.retry().maxAttempts();
        long backoffMs = properties.retry().backoffIntervalMs();
        String suffix = properties.dlt().topicSuffix();

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    String dltTopic = record.topic() + suffix;
                    log.error(
                            "Publishing exhausted record to DLT topic={} partition={} offset={} cause={}",
                            dltTopic,
                            record.partition(),
                            record.offset(),
                            ex.toString()
                    );
                    return new TopicPartition(dltTopic, record.partition());
                }
        );

        // FixedBackOff maxAttempts = retries after the first failure; then recoverer publishes to DLT.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(backoffMs, maxAttempts)
        );
        errorHandler.setCommitRecovered(true);
        errorHandler.addNotRetryableExceptions(SagaNonRetryableExceptions.types());
        return errorHandler;
    }
}
