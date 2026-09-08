package com.project.young.paymentservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.kafka.saga.dto.PaymentRefundRequestedMessage;
import com.project.young.paymentservice.messaging.error.PaymentSagaKafkaFailureStrategy;
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
import org.springframework.util.backoff.FixedBackOff;
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
public class PaymentRefundRequestedKafkaConsumerConfig {

    private final KafkaConfigData kafkaConfigData;
    private final PaymentSagaKafkaFailureStrategy failureStrategy;

    public PaymentRefundRequestedKafkaConsumerConfig(KafkaConfigData kafkaConfigData, PaymentSagaKafkaFailureStrategy failureStrategy) {
        this.kafkaConfigData = kafkaConfigData;
        this.failureStrategy = failureStrategy;
    }

    @Bean
    public ConsumerFactory<String, PaymentRefundRequestedMessage> paymentRefundRequestedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, PaymentRefundRequestedMessage.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, PaymentRefundRequestedMessage.class.getPackageName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentRefundRequestedMessage>
    paymentRefundRequestedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentRefundRequestedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentRefundRequestedConsumerFactory());
        factory.setConcurrency(1);
        failureStrategy.configure(factory);
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentRefundRequestedMessage>
    paymentRefundRequestedDltKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PaymentRefundRequestedMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentRefundRequestedConsumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }
}
