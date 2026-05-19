package com.project.young.productservice.dataaccess.projection;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Storefront PLP row — only fields exposed on the public product list API.
 */
public record PublicProductListProjection(
        UUID id,
        Long categoryId,
        String name,
        String brand,
        String mainImageUrl,
        BigDecimal basePrice
) {
}
