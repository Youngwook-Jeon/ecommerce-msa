package com.project.young.productservice.web.publicapi.dto;

import java.math.BigDecimal;
import java.util.List;

public record PublicProductSearchRequest(
        String q,
        Long categoryId,
        List<String> brands,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String sort,
        Integer page,
        Integer size
) {
    public PublicProductSearchRequest {
        brands = brands == null ? List.of() : List.copyOf(brands);
    }
}
