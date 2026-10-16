package com.project.young.productservice.messaging.consumer;

import com.project.young.productservice.application.exception.ProductSearchSynchronizationDeferredException;
import com.project.young.productservice.application.port.output.ProductSearchSynchronizationPort;
import com.project.young.productservice.messaging.dto.ProductCatalogChangedMessage;
import org.springframework.kafka.annotation.KafkaListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProductSearchSynchronizationListener {
    private final ProductSearchSynchronizationPort synchronization;
    public ProductSearchSynchronizationListener(ProductSearchSynchronizationPort synchronization) { this.synchronization = synchronization; }

    @KafkaListener(id = "product-search-sync", autoStartup = "${spring.kafka.listener.auto-startup:true}", topics = "${product-service.catalog-events.topic-name:product.catalog.changed}",
            groupId = "product-service-search-sync", containerFactory = "productSearchKafkaListenerContainerFactory")
    public void onChange(ProductCatalogChangedMessage message) {
        if (message == null || message.productId() == null || message.eventId() == null) {
            throw new IllegalArgumentException("Catalog event and product ID are required");
        }
        try {
            synchronization.synchronize(message.productId(), message.catalogRevision());
        } catch (ProductSearchSynchronizationDeferredException deferred) {
            log.debug("Product search synchronization deferred: eventId={}, productId={}, eventRevision={}, reason={}",
                    message.eventId(), message.productId(), message.catalogRevision(), deferred.getMessage());
            throw deferred;
        } catch (RuntimeException failure) {
            log.warn("Product search synchronization failed: eventId={}, productId={}, eventRevision={}",
                    message.eventId(), message.productId(), message.catalogRevision(), failure);
            throw failure;
        }
    }

    @KafkaListener(id = "product-search-dlt-replay", topics = "${product-service.search.sync.dlt-topic:product.catalog.changed.search.DLT}",
            groupId = "product-service-search-sync-dlt-replay", containerFactory = "productSearchDltReplayContainerFactory",
            autoStartup = "${product-service.search.sync.dlt-replay-enabled:false}")
    public void replay(ProductCatalogChangedMessage message) {
        onChange(message);
    }
}
