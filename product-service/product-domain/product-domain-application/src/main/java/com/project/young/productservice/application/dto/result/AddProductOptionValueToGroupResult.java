package com.project.young.productservice.application.dto.result;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record AddProductOptionValueToGroupResult(
        UUID productId,
        UUID productOptionGroupId,
        UUID productOptionValueId,
        UUID optionValueId,
        BigDecimal priceDelta
) {
}
