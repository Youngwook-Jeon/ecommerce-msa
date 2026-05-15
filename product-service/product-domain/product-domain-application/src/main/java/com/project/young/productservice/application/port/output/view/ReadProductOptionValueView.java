package com.project.young.productservice.application.port.output.view;

import lombok.Builder;
import com.project.young.productservice.domain.valueobject.OptionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Builder
public record ReadProductOptionValueView(
        UUID productOptionValueId,
        UUID optionValueId,
        BigDecimal priceDelta,
        boolean isDefault,
        OptionStatus status,
        List<ReadProductImageView> images
) {
    public ReadProductOptionValueView {
        images = images == null ? List.of() : images;
    }
}
