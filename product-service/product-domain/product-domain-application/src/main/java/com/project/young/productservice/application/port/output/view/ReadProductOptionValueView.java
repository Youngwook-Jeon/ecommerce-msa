package com.project.young.productservice.application.port.output.view;

import lombok.Builder;
import com.project.young.productservice.domain.valueobject.OptionStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record ReadProductOptionValueView(
        UUID productOptionValueId,
        UUID optionValueId,
        BigDecimal priceDelta,
        boolean isDefault,
        OptionStatus status
) {
}
