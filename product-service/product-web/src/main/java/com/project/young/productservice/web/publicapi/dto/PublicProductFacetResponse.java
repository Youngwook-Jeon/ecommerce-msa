package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PublicProductFacetResponse(
        long categoryId,
        long totalMatching,
        List<PublicProductFacetGroupResponse> facets
) {
    public PublicProductFacetResponse {
        facets = facets == null ? List.of() : List.copyOf(facets);
    }
}
