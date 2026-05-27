package com.project.young.productservice.application.dto.query;

import java.util.Arrays;

/**
 * Supported public facet groups.
 */
public enum PublicProductFacetType {
    BRAND("brand"),
    PRICE("price");

    private final String apiValue;

    PublicProductFacetType(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static PublicProductFacetType fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("facet must not be blank");
        }
        String normalized = value.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(type -> type.apiValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "facet must be one of: brand, price"
                ));
    }
}
