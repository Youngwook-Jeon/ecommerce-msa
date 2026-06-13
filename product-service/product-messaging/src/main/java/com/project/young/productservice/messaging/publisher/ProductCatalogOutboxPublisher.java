package com.project.young.productservice.messaging.publisher;

import com.project.young.kafka.product.avro.model.ProductCatalogChangedAvroModel;
import com.project.young.productservice.dataaccess.config.ProductCatalogEventProperties;
import com.project.young.productservice.dataaccess.entity.ProductCatalogOutboxEntity;
import com.project.young.productservice.dataaccess.repository.ProductCatalogOutboxJpaRepository;
import com.project.young.productservice.messaging.mapper.ProductCatalogChangedAvroMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "product-service.catalog-events", name = "relay", havingValue = "polling")
public class ProductCatalogOutboxPublisher {

    private final ProductCatalogOutboxJpaRepository productCatalogOutboxJpaRepository;
    private final ProductCatalogEventProperties productCatalogEventProperties;
    private final ProductCatalogChangedAvroMapper productCatalogChangedAvroMapper;
    private final KafkaTemplate<String, ProductCatalogChangedAvroModel> productCatalogKafkaTemplate;

    public ProductCatalogOutboxPublisher(
            ProductCatalogOutboxJpaRepository productCatalogOutboxJpaRepository,
            ProductCatalogEventProperties productCatalogEventProperties,
            ProductCatalogChangedAvroMapper productCatalogChangedAvroMapper,
            KafkaTemplate<String, ProductCatalogChangedAvroModel> productCatalogKafkaTemplate
    ) {
        this.productCatalogOutboxJpaRepository = productCatalogOutboxJpaRepository;
        this.productCatalogEventProperties = productCatalogEventProperties;
        this.productCatalogChangedAvroMapper = productCatalogChangedAvroMapper;
        this.productCatalogKafkaTemplate = productCatalogKafkaTemplate;
    }

//    @Scheduled(fixedDelayString = "${product-service.catalog-events.outbox-poll-interval-ms:2000}")
//    @Transactional
    public void publishPendingEvents() {
        List<ProductCatalogOutboxEntity> pending = productCatalogOutboxJpaRepository.findByPublishedAtIsNullOrderByCreatedAtAsc(
                PageRequest.of(0, productCatalogEventProperties.getOutboxBatchSize())
        );
        if (pending.isEmpty()) {
            return;
        }

        String topic = productCatalogEventProperties.getTopicName();
        for (ProductCatalogOutboxEntity row : pending) {
            ProductCatalogChangedAvroModel message = productCatalogChangedAvroMapper.toAvro(row);
            try {
                productCatalogKafkaTemplate.send(topic, row.getProductId().toString(), message)
                        .get(10, TimeUnit.SECONDS);
                row.setPublishedAt(Instant.now());
                productCatalogOutboxJpaRepository.save(row);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while publishing catalog outbox event {}", row.getEventId());
                return;
            } catch (ExecutionException | TimeoutException e) {
                log.error("Failed to publish catalog outbox event {}", row.getEventId(), e);
            }
        }
    }
}
