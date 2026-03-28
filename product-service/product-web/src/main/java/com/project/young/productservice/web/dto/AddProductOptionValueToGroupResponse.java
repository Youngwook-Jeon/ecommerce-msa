package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record AddProductOptionValueToGroupResponse(
        UUID productId,
        UUID productOptionGroupId,
        UUID productOptionValueId,
        UUID optionValueId,
        BigDecimal priceDelta,
        String message
) {
}
