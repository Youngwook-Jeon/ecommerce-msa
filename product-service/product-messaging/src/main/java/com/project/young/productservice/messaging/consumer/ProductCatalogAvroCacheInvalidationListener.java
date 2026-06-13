package com.project.young.productservice.messaging.consumer;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.kafka.product.avro.model.ProductCatalogChangedAvroModel;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes Avro events published by {@link com.project.young.productservice.messaging.publisher.ProductCatalogOutboxPublisher}.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "product-service.catalog-events", name = "relay", havingValue = "polling")
public class ProductCatalogAvroCacheInvalidationListener {

    private final StorefrontProductDetailCachePort storefrontProductDetailCachePort;

    public ProductCatalogAvroCacheInvalidationListener(
            StorefrontProductDetailCachePort storefrontProductDetailCachePort
    ) {
        this.storefrontProductDetailCachePort = storefrontProductDetailCachePort;
    }

    @KafkaListener(
            topics = "${product-service.catalog-events.topic-name}",
            groupId = "product-service-storefront-cache",
            containerFactory = "productCatalogKafkaListenerContainerFactory"
    )
    public void onProductCatalogChanged(ProductCatalogChangedAvroModel message) {
        UUID productId = UUID.fromString(message.getProductId().toString());
        storefrontProductDetailCachePort.evict(new ProductId(productId));
        log.info(
                "Kafka-evicted storefront PDP cache for product {} ({})",
                productId,
                message.getChangeType()
        );
    }
}
