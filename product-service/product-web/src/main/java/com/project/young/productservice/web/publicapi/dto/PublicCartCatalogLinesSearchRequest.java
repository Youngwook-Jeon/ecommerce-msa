package com.project.young.productservice.web.publicapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record PublicCartCatalogLinesSearchRequest(
        @NotNull
        @Size(max = 50)
        List<@NotNull UUID> productVariantIds
) {
    public PublicCartCatalogLinesSearchRequest {
        productVariantIds = productVariantIds == null ? List.of() : List.copyOf(productVariantIds);
    }
}
