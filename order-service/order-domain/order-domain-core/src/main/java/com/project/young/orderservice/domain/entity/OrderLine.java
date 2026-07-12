package com.project.young.orderservice.domain.entity;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.OrderLineId;

import java.util.Objects;

public class OrderLine {

    private final OrderLineId id;
    private final ProductId productId;
    private final ProductVariantId productVariantId;
    private final CartItemSnapshot snapshot;
    private final int quantity;

    private OrderLine(
            OrderLineId id,
            ProductId productId,
            ProductVariantId productVariantId,
            CartItemSnapshot snapshot,
            int quantity
    ) {
        this.id = id;
        this.productId = productId;
        this.productVariantId = productVariantId;
        this.snapshot = snapshot;
        this.quantity = quantity;
    }

    public static OrderLine fromCartItem(CartItem cartItem, OrderLineId orderLineId) {
        Objects.requireNonNull(cartItem, "cartItem must not be null");
        Objects.requireNonNull(orderLineId, "orderLineId must not be null");
        return new OrderLine(
                orderLineId,
                cartItem.getProductId(),
                cartItem.getProductVariantId(),
                cartItem.getSnapshot(),
                cartItem.getQuantity()
        );
    }

    public static OrderLine reconstitute(
            OrderLineId id,
            ProductId productId,
            ProductVariantId productVariantId,
            CartItemSnapshot snapshot,
            int quantity
    ) {
        validateQuantity(quantity);
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(productVariantId, "productVariantId must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        return new OrderLine(id, productId, productVariantId, snapshot, quantity);
    }

    public Money lineAmount() {
        return snapshot.unitPrice().multiply(quantity);
    }

    public OrderLineId getId() {
        return id;
    }

    public ProductId getProductId() {
        return productId;
    }

    public ProductVariantId getProductVariantId() {
        return productVariantId;
    }

    public CartItemSnapshot getSnapshot() {
        return snapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
