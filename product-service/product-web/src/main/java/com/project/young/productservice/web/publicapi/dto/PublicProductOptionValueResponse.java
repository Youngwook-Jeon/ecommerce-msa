package com.project.young.productservice.web.publicapi.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record PublicProductOptionValueResponse(
        UUID productOptionValueId,
        UUID optionValueId,
        String displayName,
        BigDecimal priceDelta,
        boolean isDefault,
        List<PublicProductImageResponse> images
) {
    public PublicProductOptionValueResponse {
        images = images == null ? List.of() : images;
    }
}
