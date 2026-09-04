package com.project.young.orderservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.kafka.saga.dto.PaymentCompletedMessage;
import com.project.young.kafka.saga.dto.PaymentFailedMessage;
import com.project.young.orderservice.messaging.error.KafkaListenerFailureStrategy;
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
@ConditionalOnProperty(
        prefix = "order-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PaymentSagaKafkaConsumerConfig {

    private final KafkaConfigData kafkaConfigData;
    private final KafkaListenerFailureStrategy kafkaListenerFailureStrategy;

    public PaymentSagaKafkaConsumerConfig(
            KafkaConfigData kafkaConfigData,
            KafkaListenerFailureStrategy kafkaListenerFailureStrategy
    ) {
        this.kafkaConfigData = kafkaConfigData;
        this.kafkaListenerFailureStrategy = kafkaListenerFailureStrategy;
    }

    @Bean
    public ConsumerFactory<String, PaymentCompletedMessage> paymentCompletedConsumerFactory() {
        return jsonConsumerFactory(PaymentCompletedMessage.class, false);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedMessage>
    paymentCompletedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedMessage> factory =
                listenerFactory(paymentCompletedConsumerFactory());
        // Manual ack + retry/DLT — strategy is swappable via failure-strategy property.
        kafkaListenerFailureStrategy.configure(factory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PaymentCompletedMessage> paymentCompletedDltConsumerFactory() {
        return jsonConsumerFactory(PaymentCompletedMessage.class, false);
    }

    /**
     * DLT consumer: manual ack, bounded retries on MANUAL persist failure (no DLT-of-DLT).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedMessage>
    paymentCompletedDltKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedMessage> factory =
                listenerFactory(paymentCompletedDltConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PaymentFailedMessage> paymentFailedConsumerFactory() {
        return jsonConsumerFactory(PaymentFailedMessage.class, false);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedMessage>
    paymentFailedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedMessage> factory =
                listenerFactory(paymentFailedConsumerFactory());
        // A release failure must not be auto-committed: retry it, then preserve it in the DLT.
        kafkaListenerFailureStrategy.configure(factory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, PaymentFailedMessage> paymentFailedDltConsumerFactory() {
        return jsonConsumerFactory(PaymentFailedMessage.class, false);
    }

    /**
     * DLT consumer: manual ack, bounded retries on MANUAL compensation persistence failure
     * (no DLT-of-DLT).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedMessage>
    paymentFailedDltKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentFailedMessage> factory =
                listenerFactory(paymentFailedDltConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }

    private <T> ConsumerFactory<String, T> jsonConsumerFactory(Class<T> valueType, boolean enableAutoCommit) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueType.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, valueType.getPackageName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
            ConsumerFactory<String, T> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(1);
        return factory;
    }
}
