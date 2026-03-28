package com.project.young.productservice.web.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AddProductOptionGroupResponse(
        UUID productId,
        UUID productOptionGroupId,
        UUID optionGroupId,
        int stepOrder,
        boolean required,
        int optionValueCount,
        String message
) {
}
