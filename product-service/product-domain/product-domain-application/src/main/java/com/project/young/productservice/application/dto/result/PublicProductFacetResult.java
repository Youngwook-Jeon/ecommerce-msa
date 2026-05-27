package com.project.young.productservice.application.dto.result;

import java.util.List;

public record PublicProductFacetResult(
        long categoryId,
        long totalMatching,
        List<PublicProductBrandFacetValueResult> brands,
        List<PublicProductPriceFacetBucketResult> priceBuckets
) {
    public PublicProductFacetResult {
        brands = brands == null ? List.of() : List.copyOf(brands);
        priceBuckets = priceBuckets == null ? List.of() : List.copyOf(priceBuckets);
    }
}
