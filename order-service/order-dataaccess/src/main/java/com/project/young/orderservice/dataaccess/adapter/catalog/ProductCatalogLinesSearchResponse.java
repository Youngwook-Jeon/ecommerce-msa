package com.project.young.orderservice.dataaccess.adapter.catalog;

import java.util.List;

public record ProductCatalogLinesSearchResponse(List<ProductCatalogLineResponse> lines) {

    public ProductCatalogLinesSearchResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
