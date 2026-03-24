package com.project.young.productservice.application.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ChangeProductOptionValuePriceDeltaResult(
        UUID productId,
        UUID productOptionValueId,
        BigDecimal priceDelta
) {
}
