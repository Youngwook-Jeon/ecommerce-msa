package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PublicProductPriceFacetBucketResponse(
        String id,
        String label,
        BigDecimal min,
        BigDecimal max,
        long count
) {
}
