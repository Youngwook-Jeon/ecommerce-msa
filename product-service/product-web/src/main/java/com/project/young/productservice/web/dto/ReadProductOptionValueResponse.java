package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ReadProductOptionValueResponse(
        UUID productOptionValueId,
        UUID optionValueId,
        BigDecimal priceDelta,
        boolean isDefault,
        boolean isActive
) {
}
