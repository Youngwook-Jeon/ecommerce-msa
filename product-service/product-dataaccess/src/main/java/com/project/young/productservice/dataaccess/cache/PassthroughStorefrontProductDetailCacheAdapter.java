package com.project.young.productservice.dataaccess.cache;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.StorefrontProductDetailCachePort;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.function.Supplier;

@Repository
@ConditionalOnProperty(prefix = "product-service.storefront-cache", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PassthroughStorefrontProductDetailCacheAdapter implements StorefrontProductDetailCachePort {

    @Override
    public Optional<ReadProductDetailView> findCached(ProductId productId) {
        return Optional.empty();
    }

    @Override
    public void put(ProductId productId, ReadProductDetailView view) {
        // no-op
    }

    @Override
    public void evict(ProductId productId) {
        // no-op
    }

    @Override
    public Optional<ReadProductDetailView> getOrLoad(
            ProductId productId,
            Supplier<Optional<ReadProductDetailView>> loader
    ) {
        return loader.get();
    }
}
