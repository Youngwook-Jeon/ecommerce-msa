package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
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
        String conditionType,
        List<ReadProductOptionGroupResponse> optionGroups,
        List<ReadProductVariantResponse> variants
) {
    public ReadProductDetailResponse {
        optionGroups = optionGroups == null ? List.of() : optionGroups;
        variants = variants == null ? List.of() : variants;
    }
}
