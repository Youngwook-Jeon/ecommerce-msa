package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record DeactivateProductOptionValueResult(
        UUID productId,
        UUID productOptionValueId,
        boolean active,
        BigDecimal priceDelta
) {
}
