package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PublicProductFacetResponse(
        long categoryId,
        long totalMatching,
        List<PublicProductBrandFacetValueResponse> brands,
        List<PublicProductPriceFacetBucketResponse> priceBuckets
) {
    public PublicProductFacetResponse {
        brands = brands == null ? List.of() : List.copyOf(brands);
        priceBuckets = priceBuckets == null ? List.of() : List.copyOf(priceBuckets);
    }
}
