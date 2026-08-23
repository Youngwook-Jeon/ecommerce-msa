package com.project.young.orderservice.messaging.error;

import com.project.young.kafka.config.KafkaConfigData;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Wires the active {@link KafkaListenerFailureStrategy} selected by configuration.
 */
@Configuration
@EnableConfigurationProperties(SagaKafkaConsumerErrorProperties.class)
@ConditionalOnProperty(
        prefix = "order-service.saga-events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class KafkaListenerFailureStrategyConfig {

    public static final String SAGA_DLT_KAFKA_TEMPLATE = "sagaDltKafkaTemplate";

    @Bean(name = SAGA_DLT_KAFKA_TEMPLATE)
    public KafkaTemplate<Object, Object> sagaDltKafkaTemplate(KafkaConfigData kafkaConfigData) {
        return new KafkaTemplate<>(sagaDltProducerFactory(kafkaConfigData));
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "order-service.saga-events.consumer",
            name = "failure-strategy",
            havingValue = "retry-then-dlt",
            matchIfMissing = true
    )
    public KafkaListenerFailureStrategy retryThenDltKafkaListenerFailureStrategy(
            SagaKafkaConsumerErrorProperties properties,
            @Qualifier(SAGA_DLT_KAFKA_TEMPLATE) KafkaTemplate<Object, Object> sagaDltKafkaTemplate
    ) {
        return new RetryThenDltKafkaListenerFailureStrategy(properties, sagaDltKafkaTemplate);
    }

    private static ProducerFactory<Object, Object> sagaDltProducerFactory(KafkaConfigData kafkaConfigData) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfigData.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(props);
    }
}
