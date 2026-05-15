package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReadProductOptionValueResponse(
        UUID productOptionValueId,
        UUID optionValueId,
        BigDecimal priceDelta,
        boolean isDefault,
        String status,
        List<ReadProductImageResponse> images
) {
    public ReadProductOptionValueResponse {
        images = images == null ? List.of() : images;
    }
}
