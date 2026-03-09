package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ReadProductDetailResponse(
        UUID id,
        Long categoryId,
        String name,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        String status,
        String conditionType
) {
}
