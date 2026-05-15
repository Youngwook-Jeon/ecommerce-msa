package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record UpdateProductOptionGroupVisualResponse(
        UUID productId,
        UUID productOptionGroupId,
        boolean drivesVariantImages,
        String message
) {
}
