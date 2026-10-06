package com.project.young.productservice.web.publicapi.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicProductSearchResponse(
        Query query,
        List<Product> content,
        int page,
        int size,
        long totalElements,
        long totalPages,
        List<PublicProductSearchFacetResponse> facets
) {
    public PublicProductSearchResponse {
        content = List.copyOf(content);
        facets = List.copyOf(facets);
    }

    public record Query(String q, String sort) {
    }

    public record Category(long id, String name) {
    }

    public record Product(UUID id, Category category, String name, String brand,
                          String mainImageUrl, BigDecimal basePrice) {
    }
}
