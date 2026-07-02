package com.project.young.orderservice.web.cart.mapper;

import com.project.young.orderservice.application.service.CartOwner;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.merge.CartMergeResult;
import com.project.young.orderservice.domain.merge.CartMergeSkippedLine;
import com.project.young.orderservice.domain.sync.CartSyncChange;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import com.project.young.orderservice.web.cart.dto.CartItemOptionResponse;
import com.project.young.orderservice.web.cart.dto.CartItemResponse;
import com.project.young.orderservice.web.cart.dto.CartMergeResponse;
import com.project.young.orderservice.web.cart.dto.CartMergeSkippedLineResponse;
import com.project.young.orderservice.web.cart.dto.CartResponse;
import com.project.young.orderservice.web.cart.dto.CartSyncChangeResponse;
import com.project.young.orderservice.web.cart.dto.CartSyncChangeType;
import com.project.young.orderservice.web.cart.dto.CartSyncResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class CartResponseMapper {

    public CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();

        return CartResponse.builder()
                .cartId(cart.getId().getValue())
                .ownerType(cart.getOwnerType())
                .userId(cart.getUserId() == null ? null : cart.getUserId().value())
                .items(items)
                .itemCount(cart.itemCount())
                .totalQuantity(cart.totalQuantity())
                .subtotal(cart.subtotalAmount().getAmount())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    public CartResponse emptyResponse(Optional<CartOwner> owner) {
        return owner.map(this::emptyResponse)
                .orElseGet(() -> emptyResponse(CartOwnerType.GUEST));
    }

    public CartMergeResponse noOpMergeResponse(Optional<Cart> currentCart, CartOwner owner) {
        CartResponse cart = currentCart.map(this::toResponse)
                .orElseGet(() -> emptyResponse(owner));
        return CartMergeResponse.builder()
                .cart(cart)
                .mergedLineCount(0)
                .build();
    }

    public CartMergeResponse toMergeResponse(CartMergeResult result, CartOwner owner) {
        CartResponse cart = result.cart() == null
                ? emptyResponse(owner)
                : toResponse(result.cart());
        List<CartMergeSkippedLineResponse> skipped = result.skippedLines().stream()
                .map(this::toSkippedLineResponse)
                .toList();
        List<CartSyncChangeResponse> changes = result.syncChanges().stream()
                .map(this::toChangeResponse)
                .toList();
        return CartMergeResponse.builder()
                .cart(cart)
                .mergedLineCount(result.mergedLineCount())
                .skippedLines(skipped)
                .syncChanges(changes)
                .build();
    }

    private CartMergeSkippedLineResponse toSkippedLineResponse(CartMergeSkippedLine line) {
        return CartMergeSkippedLineResponse.builder()
                .productId(line.productId().getValue())
                .productVariantId(line.productVariantId().getValue())
                .productName(line.productName())
                .quantity(line.quantity())
                .reason(line.reason())
                .build();
    }

    private CartResponse emptyResponse(CartOwner owner) {
        return switch (owner) {
            case CartOwner.User user -> CartResponse.builder()
                    .ownerType(CartOwnerType.USER)
                    .userId(user.userId().value())
                    .items(List.of())
                    .itemCount(0)
                    .totalQuantity(0)
                    .subtotal(BigDecimal.ZERO)
                    .build();
            case CartOwner.Guest guest -> CartResponse.builder()
                    .ownerType(CartOwnerType.GUEST)
                    .cartId(guest.cartId().getValue())
                    .items(List.of())
                    .itemCount(0)
                    .totalQuantity(0)
                    .subtotal(BigDecimal.ZERO)
                    .build();
        };
    }

    private CartResponse emptyResponse(CartOwnerType ownerType) {
        return CartResponse.builder()
                .ownerType(ownerType)
                .items(List.of())
                .itemCount(0)
                .totalQuantity(0)
                .subtotal(BigDecimal.ZERO)
                .build();
    }

    public CartSyncResponse toSyncResponse(CartSyncResult result) {
        List<CartSyncChangeResponse> changes = result.changes().stream()
                .map(this::toChangeResponse)
                .toList();
        return CartSyncResponse.builder()
                .cart(toResponse(result.cart()))
                .changes(changes)
                .build();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        CartItemSnapshot snapshot = item.getSnapshot();
        return CartItemResponse.builder()
                .itemId(item.getId().getValue())
                .productId(item.getProductId().getValue())
                .productVariantId(item.getProductVariantId().getValue())
                .productName(snapshot.productName())
                .brand(snapshot.brand())
                .sku(snapshot.sku())
                .imageUrl(snapshot.imageUrl())
                .unitPrice(snapshot.unitPrice().getAmount())
                .quantity(item.getQuantity())
                .lineAmount(item.lineAmount().getAmount())
                .variantOptions(snapshot.variantOptions().stream().map(this::toOptionResponse).toList())
                .build();
    }

    private CartItemOptionResponse toOptionResponse(CartItemOptionLine option) {
        return CartItemOptionResponse.builder()
                .stepOrder(option.stepOrder())
                .productOptionGroupId(option.productOptionGroupId())
                .optionGroupName(option.optionGroupName())
                .productOptionValueId(option.productOptionValueId())
                .optionValueName(option.optionValueName())
                .build();
    }

    private CartSyncChangeResponse toChangeResponse(CartSyncChange change) {
        return switch (change) {
            case CartSyncChange.PriceUpdated priceUpdated -> CartSyncChangeResponse.builder()
                    .type(CartSyncChangeType.PRICE_UPDATED)
                    .itemId(priceUpdated.itemId().getValue())
                    .previousPrice(priceUpdated.previousPrice().getAmount())
                    .currentPrice(priceUpdated.currentPrice().getAmount())
                    .build();
            case CartSyncChange.SnapshotUpdated snapshotUpdated -> CartSyncChangeResponse.builder()
                    .type(CartSyncChangeType.SNAPSHOT_UPDATED)
                    .itemId(snapshotUpdated.itemId().getValue())
                    .build();
            case CartSyncChange.QuantityAdjusted quantityAdjusted -> CartSyncChangeResponse.builder()
                    .type(CartSyncChangeType.QUANTITY_ADJUSTED)
                    .itemId(quantityAdjusted.itemId().getValue())
                    .previousQuantity(quantityAdjusted.previousQuantity())
                    .currentQuantity(quantityAdjusted.currentQuantity())
                    .build();
            case CartSyncChange.Removed removed -> CartSyncChangeResponse.builder()
                    .type(CartSyncChangeType.REMOVED)
                    .itemId(removed.itemId().getValue())
                    .productName(removed.productName())
                    .removalReason(removed.reason())
                    .build();
        };
    }
}
