package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Storefront product list item. Fields will expand when public read is wired to queries.
 */
@Builder
public record PublicProductSummaryResponse(
        UUID id,
        String name,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice
) {
}
