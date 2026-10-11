package com.project.young.productservice.dataaccess.search;

import java.math.BigDecimal;

/** Search projection. brandKey is the normalized filter key; brandLabel is the display name. */
public record OpenSearchProductDocument(
        String id, long categoryId, String categoryName, String name, String brand,
        String brandKey, String brandLabel, String description, String mainImageUrl,
        BigDecimal basePrice, String productStatus, String categoryStatus, String createdAt
) {
}
