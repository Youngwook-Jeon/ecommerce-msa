package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

@Builder
public record PublicProductBrandFacetValueResponse(
        String value,
        long count,
        boolean selected
) {
}
