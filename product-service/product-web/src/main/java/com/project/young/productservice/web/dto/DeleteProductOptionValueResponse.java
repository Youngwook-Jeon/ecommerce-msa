package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record DeleteProductOptionValueResponse(
        UUID productId,
        UUID productOptionValueId,
        String status,
        BigDecimal priceDelta,
        String message
) {
}

