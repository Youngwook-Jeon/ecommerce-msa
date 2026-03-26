package com.project.young.productservice.application.port.output.view;

import com.project.young.productservice.domain.valueobject.ProductStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReadProductVariantView(
        UUID productVariantId,
        String sku,
        int stockQuantity,
        ProductStatus status,
        BigDecimal calculatedPrice,
        List<UUID> selectedProductOptionValueIds
) {
    public ReadProductVariantView {
        selectedProductOptionValueIds = selectedProductOptionValueIds == null ? List.of() : selectedProductOptionValueIds;
    }
}
