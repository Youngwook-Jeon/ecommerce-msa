package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReadProductVariantResponse(
        UUID productVariantId,
        String sku,
        int stockQuantity,
        String status,
        BigDecimal calculatedPrice,
        List<UUID> selectedProductOptionValueIds
) {
    public ReadProductVariantResponse {
        selectedProductOptionValueIds = selectedProductOptionValueIds == null ? List.of() : selectedProductOptionValueIds;
    }
}
