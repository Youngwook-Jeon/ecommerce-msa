package com.project.young.productservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.kafka.saga.dto.InventoryReleaseRequestedMessage;
import com.project.young.productservice.messaging.error.InventoryReleaseKafkaListenerFailureStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "product-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InventoryReleaseKafkaConsumerConfig {
    private final KafkaConfigData kafkaConfigData;
    private final InventoryReleaseKafkaListenerFailureStrategy failureStrategy;

    public InventoryReleaseKafkaConsumerConfig(
            KafkaConfigData kafkaConfigData,
            InventoryReleaseKafkaListenerFailureStrategy failureStrategy
    ) {
        this.kafkaConfigData = kafkaConfigData;
        this.failureStrategy = failureStrategy;
    }

    @Bean
    ConsumerFactory<String, InventoryReleaseRequestedMessage> inventoryReleaseRequestedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, InventoryReleaseRequestedMessage.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, InventoryReleaseRequestedMessage.class.getPackageName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, InventoryReleaseRequestedMessage> inventoryReleaseRequestedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryReleaseRequestedMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryReleaseRequestedConsumerFactory());
        factory.setConcurrency(1);
        failureStrategy.configure(factory);
        return factory;
    }

    @Bean
    ConsumerFactory<String, InventoryReleaseRequestedMessage> inventoryReleaseRequestedDltConsumerFactory() {
        return inventoryReleaseRequestedConsumerFactory();
    }

    /**
     * DLT consumer: persist the manual follow-up with bounded retries and never create a DLT-of-DLT.
     */
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, InventoryReleaseRequestedMessage>
    inventoryReleaseRequestedDltKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InventoryReleaseRequestedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(inventoryReleaseRequestedDltConsumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }
}
