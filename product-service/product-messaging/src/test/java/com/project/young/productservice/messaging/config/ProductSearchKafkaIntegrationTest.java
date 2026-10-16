package com.project.young.productservice.messaging.config;

import com.project.young.kafka.config.KafkaConfigData;
import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.messaging.dto.ProductCatalogChangedMessage;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListener;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class ProductSearchKafkaIntegrationTest {
    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @Test
    void broker_retryDltAndDeferredDelivery_commitOnlyAfterHandlingAndReplayDoesNotLoop() throws Exception {
        String source = "catalog-sync-" + UUID.randomUUID();
        String dlt = source + "-DLT";
        String group = "sync-" + UUID.randomUUID();
        var settings = new KafkaConfigData();
        settings.setBootstrapServers(kafka.getBootstrapServers());
        var configuration = new ProductSearchKafkaConfiguration();
        var scheduler = configuration.productSearchKafkaPauseScheduler();
        scheduler.initialize();
        var template = configuration.productSearchDltTemplate(settings);
        var registry = new KafkaListenerEndpointRegistry();
        var factory = configuration.productSearchKafkaListenerContainerFactory(settings, new ProductSearchKafkaConfiguration.DltProducer(template), registry, scheduler, dlt);
        assertThat(factory.getConsumerFactory().getConfigurationProperties().get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        var container = factory.createContainer(source);
        container.getContainerProperties().setGroupId(group);
        var ready = new AtomicBoolean();
        var deferred = new AtomicInteger();
        var successes = new AtomicInteger();
        container.setupMessageListener((MessageListener<String, ProductCatalogChangedMessage>) record -> {
            if ("FAIL".equals(record.value().changeType())) { throw new IllegalStateException("temporary indexing failure"); }
            if ("DEFER".equals(record.value().changeType()) && !ready.get()) {
                deferred.incrementAndGet();
                throw new ProductSearchSynchronizationDeferredException("no active index");
            }
            successes.incrementAndGet();
        });
        try (var admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()));
             var observer = new KafkaConsumer<String, String>(Map.of(
                     "bootstrap.servers", kafka.getBootstrapServers(), "group.id", "observer-" + UUID.randomUUID(),
                     "auto.offset.reset", "earliest", "enable.auto.commit", false,
                     "key.deserializer", StringDeserializer.class, "value.deserializer", StringDeserializer.class))) {
            admin.createTopics(List.of(new NewTopic(source, 1, (short) 1), new NewTopic(dlt, 1, (short) 1))).all().get();
            observer.subscribe(List.of(dlt));
            container.start();
            template.send(source, "same-product", message("FAIL")).get();
            template.send(source, "same-product", message("DEFER")).get();
            template.send(source, "same-product", message("GOOD")).get();
            var deadLetter = new AtomicReference<String>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                observer.poll(Duration.ofMillis(250)).forEach(record -> deadLetter.set(record.value()));
                assertThat(deadLetter.get()).contains("product_id", "FAIL");
            });
            await().atMost(Duration.ofSeconds(20)).until(() -> deferred.get() >= 2);
            assertThat(successes.get()).isZero();
            assertThat(admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get().get(new TopicPartition(source, 0)).offset()).isEqualTo(1);
            assertThat(admin.listOffsets(Map.of(new TopicPartition(dlt, 0), org.apache.kafka.clients.admin.OffsetSpec.latest())).all().get()
                    .get(new TopicPartition(dlt, 0)).offset()).isEqualTo(1);
            ready.set(true);
            await().atMost(Duration.ofSeconds(20)).until(() -> successes.get() == 2);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(admin.listConsumerGroupOffsets(group)
                    .partitionsToOffsetAndMetadata().get().get(new TopicPartition(source, 0)).offset()).isEqualTo(3));
            var replayFactory = configuration.productSearchDltReplayContainerFactory(settings, registry, scheduler);
            var replay = replayFactory.createContainer(dlt);
            replay.getContainerProperties().setGroupId("manual-replay-" + UUID.randomUUID());
            var replayAttempts = new AtomicInteger();
            replay.setupMessageListener((MessageListener<String, ProductCatalogChangedMessage>) record -> {
                if (replayAttempts.incrementAndGet() == 1) { throw new IllegalStateException("not yet fixed"); }
            });
            try {
                replay.start();
                await().atMost(Duration.ofSeconds(20)).until(() -> replayAttempts.get() >= 2);
                assertThat(admin.listOffsets(Map.of(new TopicPartition(dlt, 0), org.apache.kafka.clients.admin.OffsetSpec.latest())).all().get()
                        .get(new TopicPartition(dlt, 0)).offset()).isEqualTo(1);
            } finally { replay.stop(); }
        } finally {
            container.stop();
            scheduler.shutdown();
            template.getProducerFactory().reset();
            template.destroy();
        }
    }

    private static ProductCatalogChangedMessage message(String type) {
        return new ProductCatalogChangedMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, type, null, null, null, 1L);
    }
}
