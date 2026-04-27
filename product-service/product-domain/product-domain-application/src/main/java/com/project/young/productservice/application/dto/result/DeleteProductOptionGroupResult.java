package com.project.young.productservice.application.dto.result;

import com.project.young.productservice.domain.valueobject.OptionStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record DeleteProductOptionGroupResult(
        UUID productId,
        UUID productOptionGroupId,
        OptionStatus status,
        double stepOrder
) {
}

