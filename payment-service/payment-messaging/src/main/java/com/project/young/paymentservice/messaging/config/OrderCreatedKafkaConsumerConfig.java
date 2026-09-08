package com.project.young.paymentservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.kafka.saga.dto.OrderCreatedMessage;
import com.project.young.paymentservice.messaging.error.PaymentSagaKafkaFailureStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(
        prefix = "payment-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderCreatedKafkaConsumerConfig {

    private final KafkaConfigData kafkaConfigData;
    private final PaymentSagaKafkaFailureStrategy failureStrategy;

    public OrderCreatedKafkaConsumerConfig(KafkaConfigData kafkaConfigData, PaymentSagaKafkaFailureStrategy failureStrategy) {
        this.kafkaConfigData = kafkaConfigData;
        this.failureStrategy = failureStrategy;
    }

    @Bean
    public ConsumerFactory<String, OrderCreatedMessage> orderCreatedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderCreatedMessage.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, OrderCreatedMessage.class.getPackageName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedMessage>
    orderCreatedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderCreatedConsumerFactory());
        factory.setConcurrency(1);
        failureStrategy.configure(factory);
        return factory;
    }
}
