package com.project.young.productservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.application.port.output.ProductSearchSynchronizationPort;
import com.project.young.productservice.messaging.consumer.ProductSearchSynchronizationListener;
import com.project.young.productservice.messaging.dto.ProductCatalogChangedMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerPausingBackOffHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerContainerPauseService;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@ConditionalOnProperty(name = {"product-service.search.enabled", "product-service.search.sync.enabled"}, havingValue = "true", matchIfMissing = true)
public class ProductSearchKafkaConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "product-service.catalog-events", name = "relay", havingValue = "debezium", matchIfMissing = true)
    ProductSearchSynchronizationListener productSearchSynchronizationListener(ProductSearchSynchronizationPort port) {
        return new ProductSearchSynchronizationListener(port);
    }

    @Bean(destroyMethod = "shutdown")
    ThreadPoolTaskScheduler productSearchKafkaPauseScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("product-search-kafka-pause-");
        return scheduler;
    }

    /** Keep this producer out of Boot's shared KafkaTemplate auto-configuration. */
    static final class DltProducer implements AutoCloseable {
        private final KafkaTemplate<Object, Object> template;
        DltProducer(KafkaTemplate<Object, Object> template) { this.template = template; }
        @Override
        public void close() {
            template.getProducerFactory().reset();
            template.destroy();
        }
    }

    @Bean(destroyMethod = "close")
    DltProducer productSearchDltProducer(KafkaConfigData kafka) {
        return new DltProducer(productSearchDltTemplate(kafka));
    }

    KafkaTemplate<Object, Object> productSearchDltTemplate(KafkaConfigData kafka) {
        var properties = new HashMap<String, Object>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        var keys = new DelegatingByTypeSerializer(Map.of(String.class, new StringSerializer(), byte[].class, new ByteArraySerializer()), true);
        var values = new DelegatingByTypeSerializer(Map.of(ProductCatalogChangedMessage.class, new JsonSerializer<>(), byte[].class, new ByteArraySerializer()), true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties, keys, values));
    }

    DefaultErrorHandler productSearchErrorHandler(KafkaTemplate<Object, Object> template,
            KafkaListenerEndpointRegistry registry, @Qualifier("productSearchKafkaPauseScheduler") ThreadPoolTaskScheduler scheduler,
            @Value("${product-service.search.sync.dlt-topic:product.catalog.changed.search.DLT}") String dltTopic) {
        var recoverer = new DeadLetterPublishingRecoverer(template, (record, exception) -> new TopicPartition(dltTopic, record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var pauses = new ContainerPausingBackOffHandler(new ListenerContainerPauseService(registry, scheduler));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 3), pauses);
        handler.setBackOffFunction((record, exception) -> isDeferred(exception)
                ? new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS) : null);
        return handler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ProductCatalogChangedMessage> productSearchKafkaListenerContainerFactory(
            KafkaConfigData kafka, DltProducer producer, KafkaListenerEndpointRegistry registry,
            @Qualifier("productSearchKafkaPauseScheduler") ThreadPoolTaskScheduler scheduler,
            @Value("${product-service.search.sync.dlt-topic:product.catalog.changed.search.DLT}") String dltTopic) {
        var factory = factory(kafka);
        factory.setCommonErrorHandler(productSearchErrorHandler(producer.template, registry, scheduler, dltTopic));
        return factory;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, ProductCatalogChangedMessage> productSearchDltReplayContainerFactory(
            KafkaConfigData kafka, KafkaListenerEndpointRegistry registry,
            @Qualifier("productSearchKafkaPauseScheduler") ThreadPoolTaskScheduler scheduler) {
        var handler = new DefaultErrorHandler(null, new FixedBackOff(5000, FixedBackOff.UNLIMITED_ATTEMPTS),
                new ContainerPausingBackOffHandler(new ListenerContainerPauseService(registry, scheduler)));
        handler.setClassifications(Map.of(Exception.class, true), true);
        var factory = factory(kafka);
        factory.setCommonErrorHandler(handler);
        return factory;
    }

    private static ConcurrentKafkaListenerContainerFactory<String, ProductCatalogChangedMessage> factory(KafkaConfigData kafka) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        properties.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        properties.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        properties.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ProductCatalogChangedMessage.class.getName());
        properties.put(JsonDeserializer.TRUSTED_PACKAGES, ProductCatalogChangedMessage.class.getPackageName());
        properties.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, ProductCatalogChangedMessage>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(1);
        return factory;
    }

    private static boolean isDeferred(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ProductSearchSynchronizationDeferredException) { return true; }
        }
        return false;
    }
}
