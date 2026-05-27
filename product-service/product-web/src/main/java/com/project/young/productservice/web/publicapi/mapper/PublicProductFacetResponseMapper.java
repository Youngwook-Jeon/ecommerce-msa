package com.project.young.productservice.web.publicapi.mapper;

import com.project.young.productservice.application.dto.result.PublicProductBrandFacetValueResult;
import com.project.young.productservice.application.dto.result.PublicProductFacetResult;
import com.project.young.productservice.application.dto.result.PublicProductPriceFacetBucketResult;
import com.project.young.productservice.web.publicapi.dto.PublicProductBrandFacetValueResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductFacetResponse;
import com.project.young.productservice.web.publicapi.dto.PublicProductPriceFacetBucketResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublicProductFacetResponseMapper {

    public PublicProductFacetResponse toResponse(PublicProductFacetResult result) {
        List<PublicProductBrandFacetValueResponse> brands = result.brands().stream()
                .map(this::toBrandResponse)
                .toList();

        List<PublicProductPriceFacetBucketResponse> priceBuckets = result.priceBuckets().stream()
                .map(this::toPriceBucketResponse)
                .toList();

        return PublicProductFacetResponse.builder()
                .categoryId(result.categoryId())
                .totalMatching(result.totalMatching())
                .brands(brands)
                .priceBuckets(priceBuckets)
                .build();
    }

    private PublicProductBrandFacetValueResponse toBrandResponse(PublicProductBrandFacetValueResult value) {
        return PublicProductBrandFacetValueResponse.builder()
                .value(value.value())
                .count(value.count())
                .selected(value.selected())
                .build();
    }

    private PublicProductPriceFacetBucketResponse toPriceBucketResponse(PublicProductPriceFacetBucketResult bucket) {
        return PublicProductPriceFacetBucketResponse.builder()
                .id(bucket.id())
                .label(bucket.label())
                .min(bucket.min())
                .max(bucket.max())
                .count(bucket.count())
                .build();
    }
}
