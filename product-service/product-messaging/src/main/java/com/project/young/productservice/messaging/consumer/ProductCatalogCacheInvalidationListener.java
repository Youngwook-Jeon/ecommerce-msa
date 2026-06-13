package com.project.young.productservice.messaging.consumer;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import com.project.young.productservice.messaging.dto.ProductCatalogChangedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Idempotent secondary eviction — primary eviction runs post-commit via Spring events.
 * Consumes Debezium JSON from {@code product.catalog.changed}.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "product-service.catalog-events",
        name = "relay",
        havingValue = "debezium",
        matchIfMissing = true
)
public class ProductCatalogCacheInvalidationListener {

    private final StorefrontProductDetailCachePort storefrontProductDetailCachePort;

    public ProductCatalogCacheInvalidationListener(
            StorefrontProductDetailCachePort storefrontProductDetailCachePort
    ) {
        this.storefrontProductDetailCachePort = storefrontProductDetailCachePort;
    }

    @KafkaListener(
            topics = "${product-service.catalog-events.topic-name}",
            groupId = "product-service-storefront-cache",
            containerFactory = "productCatalogKafkaListenerContainerFactory"
    )
    public void onProductCatalogChanged(ProductCatalogChangedMessage message) {
        if (message == null || message.productId() == null) {
            log.warn("Skipping catalog invalidation message with missing productId");
            return;
        }
        storefrontProductDetailCachePort.evict(new ProductId(message.productId()));
        log.info(
                "Kafka-evicted storefront PDP cache for product {} ({})",
                message.productId(),
                message.changeType()
        );
    }
}
