package com.project.young.productservice.application.port.output.view;

import com.project.young.productservice.domain.valueobject.ConditionType;
import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReadProductDetailView(
        UUID id,
        Long categoryId,
        String name,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        ProductStatus status,
        ConditionType conditionType,
        List<ReadProductOptionGroupView> optionGroups,
        List<ReadProductVariantView> variants
) {
    public ReadProductDetailView {
        optionGroups = optionGroups == null ? List.of() : optionGroups;
        variants = variants == null ? List.of() : variants;
    }
}
