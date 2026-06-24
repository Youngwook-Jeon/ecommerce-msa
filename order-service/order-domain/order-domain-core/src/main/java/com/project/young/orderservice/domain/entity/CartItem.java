package com.project.young.orderservice.domain.entity;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.domain.exception.CartDomainException;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;

import java.util.Objects;

public class CartItem {

    private final CartItemId id;
    private final ProductId productId;
    private final ProductVariantId productVariantId;
    private CartItemSnapshot snapshot;
    private int quantity;

    private CartItem(
            CartItemId id,
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

    public static CartItem createNew(
            CartItemId id,
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
        return new CartItem(id, productId, productVariantId, snapshot, quantity);
    }

    public static CartItem reconstitute(
            CartItemId id,
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
        return new CartItem(id, productId, productVariantId, snapshot, quantity);
    }

    public void mergeQuantity(int additionalQuantity) {
        if (additionalQuantity <= 0) {
            throw new CartDomainException("Additional quantity must be positive.");
        }
        this.quantity += additionalQuantity;
    }

    public void changeQuantity(int newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
    }

    public void applySnapshot(CartItemSnapshot newSnapshot) {
        Objects.requireNonNull(newSnapshot, "snapshot must not be null");
        this.snapshot = newSnapshot;
    }

    public Money lineAmount() {
        return snapshot.unitPrice().multiply(quantity);
    }

    public CartItemId getId() {
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

    boolean hasSameVariant(ProductVariantId variantId) {
        return productVariantId.equals(variantId);
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new CartDomainException("Quantity must be positive.");
        }
    }
}
