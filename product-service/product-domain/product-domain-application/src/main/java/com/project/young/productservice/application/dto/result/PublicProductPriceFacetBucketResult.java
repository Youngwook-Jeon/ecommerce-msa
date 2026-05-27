package com.project.young.productservice.application.dto.result;

import java.math.BigDecimal;

public record PublicProductPriceFacetBucketResult(
        String id,
        String label,
        BigDecimal min,
        BigDecimal max,
        long count
) {
}
