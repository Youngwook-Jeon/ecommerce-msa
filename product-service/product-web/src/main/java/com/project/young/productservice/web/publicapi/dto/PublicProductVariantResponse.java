package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record PublicProductVariantResponse(
        UUID productVariantId,
        String sku,
        int stockQuantity,
        BigDecimal calculatedPrice,
        String mainImageUrl,
        List<UUID> selectedProductOptionValueIds
) {
    public PublicProductVariantResponse {
        selectedProductOptionValueIds =
                selectedProductOptionValueIds == null ? List.of() : selectedProductOptionValueIds;
    }
}
