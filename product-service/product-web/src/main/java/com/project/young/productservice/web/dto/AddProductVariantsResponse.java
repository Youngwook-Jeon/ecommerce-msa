package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.List;
import java.util.Objects;

@Builder
public record AddProductVariantsResponse(
        List<AddProductVariantResponse> variants
) {
    public AddProductVariantsResponse {
        variants = Objects.requireNonNull(variants, "variants must not be null");
    }
}

