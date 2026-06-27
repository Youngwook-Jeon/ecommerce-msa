package com.project.young.orderservice.web.cart.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CartSyncResponse(
        CartResponse cart,
        List<CartSyncChangeResponse> changes
) {
    public CartSyncResponse {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }
}
