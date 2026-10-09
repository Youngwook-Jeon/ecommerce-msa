package com.project.young.productservice.application.dto.result;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One complete search response from the search engine, including exact totals and facets. */
public record ProductSearchResult(
        List<Product> content,
        long totalElements,
        List<Brand> brands,
        boolean hasMoreBrands,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
    public ProductSearchResult {
        content = List.copyOf(content);
        brands = List.copyOf(brands);
        if (totalElements < 0) {
            throw new IllegalArgumentException("totalElements must be nonnegative");
        }
    }

    public record Product(UUID id, long categoryId, String categoryName, String name, String brand,
                          String mainImageUrl, BigDecimal basePrice) {
    }

    public record Brand(String value, String label, long count) {
    }
}
