package com.project.young.paymentservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "payment-service.saga-events", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CustomerRefundDltKafkaConsumerConfig {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> customerRefundDltKafkaListenerContainerFactory(
            KafkaConfigData config) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        // No recovery/discard or DLT-of-DLT on a storage outage.
        var errorHandler = new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        errorHandler.setClassifications(Map.of(Exception.class, true), true);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(1);
        return factory;
    }
}
