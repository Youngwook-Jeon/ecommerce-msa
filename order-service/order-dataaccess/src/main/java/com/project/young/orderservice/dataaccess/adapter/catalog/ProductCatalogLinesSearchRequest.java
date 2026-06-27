package com.project.young.orderservice.dataaccess.adapter.catalog;

import java.util.List;
import java.util.UUID;

public record ProductCatalogLinesSearchRequest(List<UUID> productVariantIds) {

    public ProductCatalogLinesSearchRequest {
        productVariantIds = productVariantIds == null ? List.of() : List.copyOf(productVariantIds);
    }
}
