package com.project.young.productservice.application.dto.query;

import java.util.Arrays;

/**
 * Storefront PLP sort. API values: {@code newest}, {@code price_asc}, {@code price_desc}, {@code relevance}.
 */
public enum PublicProductSort {
    NEWEST("newest"),
    PRICE_ASC("price_asc"),
    PRICE_DESC("price_desc"),
    RELEVANCE("relevance");

    private final String apiValue;

    PublicProductSort(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }

    public static PublicProductSort fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return NEWEST;
        }
        String normalized = value.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(sort -> sort.apiValue.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "sort must be one of: newest, price_asc, price_desc, relevance"
                ));
    }
}
