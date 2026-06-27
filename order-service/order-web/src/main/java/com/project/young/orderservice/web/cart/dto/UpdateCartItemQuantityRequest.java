package com.project.young.orderservice.web.cart.dto;

import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record UpdateCartItemQuantityRequest(
        @Positive int quantity
) {
}
