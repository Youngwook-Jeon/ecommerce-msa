package com.project.young.productservice.application.dto.result;

public record PublicProductBrandFacetValueResult(
        String value,
        long count,
        boolean selected
) {
}
