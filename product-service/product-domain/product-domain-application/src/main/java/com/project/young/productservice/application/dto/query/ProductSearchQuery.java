package com.project.young.productservice.application.dto.query;

import java.math.BigDecimal;
import java.util.List;

/** Raw search input; validate before executing a search. Null scalar values mean omitted parameters. */
public record ProductSearchQuery(
        String q,
        Long categoryId,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String sort,
        Integer page,
        Integer size
) {
    public ProductSearchQuery {
        brands = brands == null ? List.of() : List.copyOf(brands);
    }
}
