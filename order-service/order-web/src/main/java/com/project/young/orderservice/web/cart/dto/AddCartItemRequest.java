package com.project.young.orderservice.web.cart.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

import java.util.UUID;

@Builder
public record AddCartItemRequest(
        @NotNull UUID productId,
        @NotNull UUID productVariantId,
        @Positive int quantity
) {
}
