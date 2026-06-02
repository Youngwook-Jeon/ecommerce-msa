package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Storefront PDP — {@code GET /public/products/{productId}}.
 * See {@code product-service/docs/STOREFRONT_PRODUCT_DETAIL.md}.
 */
@Builder
public record PublicProductDetailResponse(
        UUID id,
        Long categoryId,
        String name,
        String description,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice,
        String status,
        String conditionType,
        boolean purchasable,
        boolean listedInCatalog,
        List<PublicProductImageResponse> images,
        List<PublicProductOptionGroupResponse> optionGroups,
        List<PublicProductVariantResponse> variants
) {
    public PublicProductDetailResponse {
        images = images == null ? List.of() : images;
        optionGroups = optionGroups == null ? List.of() : optionGroups;
        variants = variants == null ? List.of() : variants;
    }
}
