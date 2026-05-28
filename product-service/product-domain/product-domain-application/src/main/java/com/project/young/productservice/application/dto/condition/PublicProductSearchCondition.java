package com.project.young.productservice.application.dto.condition;

import java.math.BigDecimal;
import java.util.List;

/**
 * Normalized storefront product list filters (category is always required at the API boundary).
 */
public record PublicProductSearchCondition(
        long categoryId,
        String keyword,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
    public PublicProductSearchCondition {
        brands = brands == null ? List.of() : List.copyOf(brands);
    }

    public String normalizedKeyword() {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public List<String> normalizedBrands() {
        return brands.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
