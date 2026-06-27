package com.project.young.orderservice.web.cart.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record CartItemOptionResponse(
        int stepOrder,
        UUID productOptionGroupId,
        String optionGroupName,
        UUID productOptionValueId,
        String optionValueName
) {
}
