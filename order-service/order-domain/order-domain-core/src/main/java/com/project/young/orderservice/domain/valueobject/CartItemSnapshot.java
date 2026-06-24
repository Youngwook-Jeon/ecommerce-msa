package com.project.young.orderservice.domain.valueobject;

import com.project.young.common.domain.valueobject.Money;

import java.util.List;
import java.util.Objects;

public record CartItemSnapshot(
        String productName,
        String brand,
        String sku,
        String imageUrl,
        Money unitPrice,
        List<CartItemOptionLine> variantOptions
) {

    public CartItemSnapshot {
        Objects.requireNonNull(productName, "productName must not be null");
        Objects.requireNonNull(sku, "sku must not be null");
        Objects.requireNonNull(unitPrice, "unitPrice must not be null");
        variantOptions = variantOptions == null ? List.of() : List.copyOf(variantOptions);
        if (productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
    }
}
