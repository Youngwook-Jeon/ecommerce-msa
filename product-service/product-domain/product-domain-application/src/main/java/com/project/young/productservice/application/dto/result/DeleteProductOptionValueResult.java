package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record DeleteProductOptionValueResult(
        UUID productId,
        UUID productOptionValueId,
        OptionStatus status,
        BigDecimal priceDelta
) {
}

