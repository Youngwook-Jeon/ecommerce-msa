package com.project.young.productservice.application.dto.condition;

import com.project.young.productservice.application.dto.query.PublicProductSort;

import java.math.BigDecimal;
import java.util.List;

/** Normalized search criteria, independent of HTTP and search-engine types. */
public record ProductSearchCriteria(
        String q,
        Long categoryId,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        PublicProductSort sort,
        int page,
        int size
) {
    public ProductSearchCriteria {
        brands = List.copyOf(brands);
    }

    public long offset() {
        return (long) page * size;
    }
}
