package com.project.young.productservice.application.dto.query;

import java.math.BigDecimal;
import java.util.List;

/**
 * Raw storefront product list request (validated in {@link com.project.young.productservice.application.service.PublicProductQueryService}).
 */
public record PublicProductListQuery(
        long categoryId,
        int page,
        int size,
        String q,
        String sort,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
    public PublicProductListQuery {
        brands = brands == null ? List.of() : List.copyOf(brands);
    }
}
