package com.project.young.paymentservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.kafka.saga.dto.CustomerRefundRequestedMessage;
import com.project.young.paymentservice.messaging.error.CustomerRefundKafkaFailureStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "payment-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CustomerRefundRequestedKafkaConsumerConfig {

    private final KafkaConfigData kafkaConfigData;
    private final CustomerRefundKafkaFailureStrategy failureStrategy;

    public CustomerRefundRequestedKafkaConsumerConfig(
            KafkaConfigData kafkaConfigData,
            CustomerRefundKafkaFailureStrategy failureStrategy
    ) {
        this.kafkaConfigData = kafkaConfigData;
        this.failureStrategy = failureStrategy;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerRefundRequestedMessage>
    customerRefundRequestedKafkaListenerContainerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.getBootstrapServers());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        properties.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        properties.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, CustomerRefundRequestedMessage.class.getName());
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, CustomerRefundRequestedMessage.class.getPackageName());
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        ConcurrentKafkaListenerContainerFactory<String, CustomerRefundRequestedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        factory.setConcurrency(1);
        failureStrategy.configure(factory);
        return factory;
    }
}
