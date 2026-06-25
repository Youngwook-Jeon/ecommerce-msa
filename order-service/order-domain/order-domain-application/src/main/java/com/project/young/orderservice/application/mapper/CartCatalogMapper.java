package com.project.young.orderservice.application.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
import com.project.young.orderservice.application.port.output.view.CartCatalogOptionLineView;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.sync.CartCatalogLineState;
import com.project.young.orderservice.domain.sync.CartSyncRemovalReason;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CartCatalogMapper {

    private CartCatalogMapper() {
    }

    public static CartItemSnapshot toSnapshot(CartCatalogLineView view) {
        return new CartItemSnapshot(
                view.productName(),
                view.brand(),
                view.sku(),
                view.imageUrl(),
                new Money(view.unitPrice()),
                view.variantOptions().stream().map(CartCatalogMapper::toOptionLine).toList()
        );
    }

    public static CartCatalogLineState toLineState(CartCatalogLineView view) {
        if (!view.purchasable()) {
            return CartCatalogLineState.unavailable(CartSyncRemovalReason.NOT_PURCHASABLE);
        }
        if (view.stockQuantity() <= 0) {
            return CartCatalogLineState.unavailable(CartSyncRemovalReason.OUT_OF_STOCK);
        }
        return CartCatalogLineState.available(toSnapshot(view), view.stockQuantity());
    }

    /**
     * Maps batch-resolved catalog views to per-cart-item states for {@link Cart#reconcileWithCatalog(Map)}.
     */
    public static Map<CartItemId, CartCatalogLineState> toLineStateByItemId(
            Cart cart,
            Map<UUID, CartCatalogLineView> resolvedByVariantId
    ) {
        Map<CartItemId, CartCatalogLineState> catalogByItemId = new HashMap<>();
        for (CartItem item : cart.getItems()) {
            UUID variantId = item.getProductVariantId().getValue();
            CartCatalogLineView view = resolvedByVariantId.get(variantId);
            if (view == null) {
                catalogByItemId.put(item.getId(), CartCatalogLineState.unavailable(CartSyncRemovalReason.VARIANT_NOT_FOUND));
            } else {
                catalogByItemId.put(item.getId(), toLineState(view));
            }
        }
        return catalogByItemId;
    }

    private static CartItemOptionLine toOptionLine(CartCatalogOptionLineView view) {
        return new CartItemOptionLine(
                view.stepOrder(),
                view.productOptionGroupId(),
                view.optionGroupName(),
                view.productOptionValueId(),
                view.optionValueName()
        );
    }
}
