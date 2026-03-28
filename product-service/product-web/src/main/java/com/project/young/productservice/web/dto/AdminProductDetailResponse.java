package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
        Instant updatedAt,
        List<ReadProductOptionGroupResponse> optionGroups,
        List<ReadProductVariantResponse> variants
) {
    public AdminProductDetailResponse {
        optionGroups = optionGroups == null ? List.of() : optionGroups;
        variants = variants == null ? List.of() : variants;
    }
}
