package com.project.young.productservice.application.dto.query;

import java.math.BigDecimal;
import java.util.List;

/**
 * Raw storefront facet request (validated in application service).
 */
public record PublicProductFacetQuery(
        long categoryId,
        String q,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<PublicProductFacetType> facets
) {
    public PublicProductFacetQuery {
        brands = brands == null ? List.of() : List.copyOf(brands);
        facets = facets == null ? List.of() : List.copyOf(facets);
    }
}
