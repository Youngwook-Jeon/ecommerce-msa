package com.project.young.productservice.application.dto.query;

import java.math.BigDecimal;

/**
 * Raw storefront product list request (validated in {@link com.project.young.productservice.application.service.PublicProductQueryService}).
 */
public record PublicProductListQuery(
        long categoryId,
        int page,
        int size,
        String q,
        String sort,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
