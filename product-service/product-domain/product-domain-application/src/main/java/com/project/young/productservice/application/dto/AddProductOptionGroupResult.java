package com.project.young.productservice.application.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AddProductOptionGroupResult(
        UUID productId,
        UUID productOptionGroupId,
        UUID optionGroupId,
        int stepOrder,
        boolean required,
        int optionValueCount
) {
}
