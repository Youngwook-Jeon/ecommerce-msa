package com.project.young.productservice.application.port.output.view;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Storefront product list row — application read model for public PLP (not {@link ReadProductView}).
 */
@Builder
public record ReadPublicProductSummaryView(
        UUID id,
        Long categoryId,
        String name,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice
) {
}
