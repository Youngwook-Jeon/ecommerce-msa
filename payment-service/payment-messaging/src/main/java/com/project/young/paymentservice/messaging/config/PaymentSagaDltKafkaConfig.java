package com.project.young.paymentservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.paymentservice.messaging.error.PaymentSagaKafkaErrorProperties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(PaymentSagaKafkaErrorProperties.class)
@ConditionalOnProperty(prefix = "payment-service.saga-events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaymentSagaDltKafkaConfig {
    @Bean
    KafkaTemplate<Object, Object> paymentSagaDltKafkaTemplate(KafkaConfigData data) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, data.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }
}
