package com.project.young.productservice.application.port.output;

import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.productservice.application.port.output.view.ReadProductDetailView;

import java.util.Optional;
import java.util.function.Supplier;

public interface StorefrontProductDetailCachePort {

    Optional<ReadProductDetailView> findCached(ProductId productId);

    void put(ProductId productId, ReadProductDetailView view);

    void evict(ProductId productId);

    /**
     * Cache-aside with stampede protection (implementation-defined).
     */
    Optional<ReadProductDetailView> getOrLoad(
            ProductId productId,
            Supplier<Optional<ReadProductDetailView>> loader
    );
}
