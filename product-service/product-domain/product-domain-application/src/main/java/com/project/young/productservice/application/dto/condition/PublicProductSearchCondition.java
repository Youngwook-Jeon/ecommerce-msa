package com.project.young.productservice.application.dto.condition;

import java.math.BigDecimal;

/**
 * Normalized storefront product list filters (category is always required at the API boundary).
 */
public record PublicProductSearchCondition(
        long categoryId,
        String keyword,
        String brand,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public String normalizedBrand() {
        return brand == null || brand.isBlank() ? null : brand.trim();
    }
}
