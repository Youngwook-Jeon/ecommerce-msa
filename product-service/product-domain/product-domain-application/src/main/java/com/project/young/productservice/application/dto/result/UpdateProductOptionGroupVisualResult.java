package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateProductOptionGroupVisualResult(
        UUID productId,
        UUID productOptionGroupId,
        boolean drivesVariantImages
) {
}
