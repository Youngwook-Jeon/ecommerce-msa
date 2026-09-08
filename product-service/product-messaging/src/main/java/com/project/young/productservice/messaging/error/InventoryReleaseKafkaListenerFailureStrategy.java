package com.project.young.productservice.messaging.error;

import com.project.young.productservice.domain.exception.InventoryDomainException;
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

/**
 * Retries transient inventory-release failures, then preserves the record in its source-topic DLT.
 */
@Component
public class InventoryReleaseKafkaListenerFailureStrategy {

    private static final Logger log = LoggerFactory.getLogger(InventoryReleaseKafkaListenerFailureStrategy.class);

    private final InventoryReleaseKafkaConsumerErrorProperties properties;
    private final KafkaTemplate<Object, Object> inventoryReleaseDltKafkaTemplate;

    public InventoryReleaseKafkaListenerFailureStrategy(
            InventoryReleaseKafkaConsumerErrorProperties properties,
            KafkaTemplate<Object, Object> inventoryReleaseDltKafkaTemplate
    ) {
        this.properties = properties;
        this.inventoryReleaseDltKafkaTemplate = inventoryReleaseDltKafkaTemplate;
    }

    public void configure(ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(createErrorHandler());
    }

    private DefaultErrorHandler createErrorHandler() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                inventoryReleaseDltKafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) -> {
                    String dltTopic = record.topic() + properties.dlt().topicSuffix();
                    log.error(
                            "Publishing exhausted inventory-release record to DLT topic={} partition={} offset={} cause={}",
                            dltTopic, record.partition(), record.offset(), exception.toString());
                    return new TopicPartition(dltTopic, record.partition());
                }
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(properties.retry().backoffIntervalMs(), properties.retry().maxAttempts())
        );
        errorHandler.setCommitRecovered(true);
        errorHandler.addNotRetryableExceptions(InventoryDomainException.class);
        return errorHandler;
    }
}
