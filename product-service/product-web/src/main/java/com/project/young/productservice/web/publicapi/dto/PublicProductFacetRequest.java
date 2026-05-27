package com.project.young.productservice.web.publicapi.dto;

import com.project.young.productservice.application.dto.query.PublicProductFacetType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Parsed request model for storefront facet endpoint.
 */
public record PublicProductFacetRequest(
        long categoryId,
        String q,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<PublicProductFacetType> facets
) {
    public PublicProductFacetRequest {
        brands = brands == null ? List.of() : List.copyOf(brands);
        facets = facets == null ? List.of() : List.copyOf(facets);
    }
}
