package com.project.young.productservice.dataaccess.projection;

import com.project.young.productservice.dataaccess.enums.ConditionTypeEntity;
import com.project.young.productservice.dataaccess.enums.ProductStatusEntity;

import java.math.BigDecimal;
import java.util.UUID;

public record AdminProductListProjection(
        UUID id,
        Long categoryId,
        String name,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        ProductStatusEntity status,
        ConditionTypeEntity conditionType
) {
}
