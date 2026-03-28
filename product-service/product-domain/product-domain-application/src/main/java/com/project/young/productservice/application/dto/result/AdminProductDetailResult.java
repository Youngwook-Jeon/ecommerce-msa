package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.application.port.output.view.ReadProductOptionGroupView;
import com.project.young.productservice.application.port.output.view.ReadProductVariantView;
import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record AdminProductDetailResult(
        UUID id,
        Long categoryId,
        String name,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        ProductStatus status,
        ConditionType conditionType,
        Instant createdAt,
        Instant updatedAt,
        List<ReadProductOptionGroupView> optionGroups,
        List<ReadProductVariantView> variants
) {
    public AdminProductDetailResult {
        optionGroups = optionGroups == null ? List.of() : optionGroups;
        variants = variants == null ? List.of() : variants;
    }
}
