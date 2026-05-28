package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PublicProductFacetGroupResponse(
        String key,
        String type,
        List<PublicProductBrandFacetValueResponse> terms,
        List<PublicProductPriceFacetBucketResponse> ranges
) {
    public PublicProductFacetGroupResponse {
        terms = terms == null ? List.of() : List.copyOf(terms);
        ranges = ranges == null ? List.of() : List.copyOf(ranges);
    }
}
