package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record AdminProductDetailResponse(
        UUID id,
        Long categoryId,
        String name,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        String status,
        String conditionType,
        Instant createdAt,
        Instant updatedAt
) {
}
