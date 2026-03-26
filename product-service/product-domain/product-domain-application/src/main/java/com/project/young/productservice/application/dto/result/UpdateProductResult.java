package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record UpdateProductResult(
        UUID id,
        String name,
        Long categoryId,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        ProductStatus status,
        ConditionType conditionType
) {
}