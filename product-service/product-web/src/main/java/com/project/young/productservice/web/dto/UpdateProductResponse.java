package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record UpdateProductResponse(
        UUID id,
        String name,
        Long categoryId,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        String status,
        String conditionType,
        String message
) {
}
