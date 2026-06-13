package com.project.young.productservice.application.event;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.dto.event.StorefrontProductDetailCacheEvictRequestedEvent;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts Redis only after the DB transaction commits so Redis failures cannot roll back writes.
 * Kafka/Debezium consumer provides secondary eviction if this step fails.
 */
@Component
@Slf4j
public class StorefrontProductDetailCacheEvictListener {

    private final StorefrontProductDetailCachePort storefrontProductDetailCachePort;

    public StorefrontProductDetailCacheEvictListener(
            StorefrontProductDetailCachePort storefrontProductDetailCachePort
    ) {
        this.storefrontProductDetailCachePort = storefrontProductDetailCachePort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStorefrontProductDetailCacheEvictRequested(
            StorefrontProductDetailCacheEvictRequestedEvent event
    ) {
        try {
            storefrontProductDetailCachePort.evict(new ProductId(event.productId()));
            log.debug(
                    "Post-commit Redis evict for storefront PDP cache: product {} ({})",
                    event.productId(),
                    event.changeType()
            );
        } catch (RuntimeException ex) {
            log.error(
                    "Post-commit Redis evict failed for product {} ({}); relying on Kafka secondary eviction",
                    event.productId(),
                    event.changeType(),
                    ex
            );
        }
    }
}
