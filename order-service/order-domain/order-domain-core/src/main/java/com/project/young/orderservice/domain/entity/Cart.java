package com.project.young.orderservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.domain.exception.CartDomainException;
import com.project.young.orderservice.domain.exception.CartItemNotFoundException;
import com.project.young.orderservice.domain.sync.CartCatalogLineState;
import com.project.young.orderservice.domain.sync.CartSyncChange;
import com.project.young.orderservice.domain.sync.CartSyncRemovalReason;
import com.project.young.orderservice.domain.sync.CartSyncResult;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import com.project.young.orderservice.domain.valueobject.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class Cart extends AggregateRoot<CartId> {

    public static final int MAX_LINE_ITEMS = 50;

    private final CartOwnerType ownerType;
    private final UserId userId;
    private final List<CartItem> items;
    private Instant createdAt;
    private Instant updatedAt;

    private Cart(Builder builder) {
        super.setId(builder.cartId);
        this.ownerType = builder.ownerType;
        this.userId = builder.userId;
        this.items = new ArrayList<>(builder.items);
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Cart createForUser(UserId userId, CartId cartId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(cartId, "cartId must not be null");
        return builder()
                .cartId(cartId)
                .ownerType(CartOwnerType.USER)
                .userId(userId)
                .items(List.of())
                .build();
    }

    public static Cart createForGuest(CartId cartId) {
        Objects.requireNonNull(cartId, "cartId must not be null");
        return builder()
                .cartId(cartId)
                .ownerType(CartOwnerType.GUEST)
                .items(List.of())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public CartItem addOrMergeItem(
            ProductId productId,
            ProductVariantId productVariantId,
            CartItemSnapshot snapshot,
            int quantity,
            CartItemId newItemId
    ) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(productVariantId, "productVariantId must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        if (quantity <= 0) {
            throw new CartDomainException("Quantity must be positive.");
        }

        Optional<CartItem> existing = findItemByVariant(productVariantId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.mergeQuantity(quantity);
            item.applySnapshot(snapshot);
            touchUpdatedAt();
            return item;
        }

        if (items.size() >= MAX_LINE_ITEMS) {
            throw new CartDomainException("Cart cannot contain more than " + MAX_LINE_ITEMS + " distinct items.");
        }

        Objects.requireNonNull(newItemId, "newItemId must not be null when adding a new cart line");
        CartItem newItem = CartItem.createNew(newItemId, productId, productVariantId, snapshot, quantity);
        items.add(newItem);
        touchUpdatedAt();
        return newItem;
    }

    public void updateItemQuantity(CartItemId itemId, int quantity) {
        CartItem item = getItem(itemId);
        item.changeQuantity(quantity);
        touchUpdatedAt();
    }

    public void removeItem(CartItemId itemId) {
        CartItem item = getItem(itemId);
        items.remove(item);
        touchUpdatedAt();
    }

    public void clearItems() {
        if (items.isEmpty()) {
            return;
        }
        items.clear();
        touchUpdatedAt();
    }

    /**
     * Applies catalog validation results from {@code POST /carts/current/sync}.
     * Removes unavailable lines, clamps quantity to stock, and refreshes snapshots.
     */
    public CartSyncResult reconcileWithCatalog(Map<CartItemId, CartCatalogLineState> catalogByItemId) {
        Objects.requireNonNull(catalogByItemId, "catalogByItemId must not be null");

        List<CartSyncChange> changes = new ArrayList<>();
        List<CartItem> toRemove = new ArrayList<>();

        for (CartItem item : List.copyOf(items)) {
            CartItemId itemId = item.getId();
            CartCatalogLineState catalog = catalogByItemId.get(itemId);

            if (catalog == null || !catalog.available()) {
                toRemove.add(item);
                changes.add(new CartSyncChange.Removed(
                        itemId,
                        item.getSnapshot().productName(),
                        catalog == null ? CartSyncRemovalReason.VARIANT_NOT_FOUND : catalog.removalReason()
                ));
                continue;
            }

            if (catalog.stockQuantity() < item.getQuantity()) {
                int previousQuantity = item.getQuantity();
                item.changeQuantity(catalog.stockQuantity());
                changes.add(new CartSyncChange.QuantityAdjusted(
                        itemId,
                        previousQuantity,
                        catalog.stockQuantity()
                ));
            }

            Money previousPrice = item.getSnapshot().unitPrice();
            CartItemSnapshot nextSnapshot = catalog.snapshot();
            if (!previousPrice.equals(nextSnapshot.unitPrice())) {
                changes.add(new CartSyncChange.PriceUpdated(
                        itemId,
                        previousPrice,
                        nextSnapshot.unitPrice()
                ));
            } else if (!item.getSnapshot().equals(nextSnapshot)) {
                changes.add(new CartSyncChange.SnapshotUpdated(itemId));
            }

            item.applySnapshot(nextSnapshot);
        }

        items.removeAll(toRemove);
        if (!changes.isEmpty()) {
            touchUpdatedAt();
        }

        return new CartSyncResult(this, changes);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public int itemCount() {
        return items.size();
    }

    public int totalQuantity() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public Money subtotalAmount() {
        return items.stream()
                .map(CartItem::lineAmount)
                .reduce(Money.ZERO, Money::add);
    }

    /**
     * Returns true only when the current cart still represents exactly the purchased lines.
     * This prevents payment completion from deleting items added or changed after checkout.
     */
    public boolean hasSameItemsAs(List<OrderLine> orderLines) {
        Objects.requireNonNull(orderLines, "orderLines must not be null");
        Map<ProductVariantId, Integer> cartQuantities = items.stream()
                .collect(Collectors.toMap(
                        CartItem::getProductVariantId,
                        CartItem::getQuantity,
                        Integer::sum
                ));
        Map<ProductVariantId, Integer> orderQuantities = orderLines.stream()
                .collect(Collectors.toMap(
                        OrderLine::getProductVariantId,
                        OrderLine::getQuantity,
                        Integer::sum
                ));
        return cartQuantities.equals(orderQuantities);
    }

    public List<CartItem> getItems() {
        return List.copyOf(items);
    }

    public CartOwnerType getOwnerType() {
        return ownerType;
    }

    public UserId getUserId() {
        return userId;
    }

    public boolean isGuestCart() {
        return ownerType == CartOwnerType.GUEST;
    }

    public boolean isUserCart() {
        return ownerType == CartOwnerType.USER;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private Optional<CartItem> findItemByVariant(ProductVariantId productVariantId) {
        return items.stream()
                .filter(item -> item.hasSameVariant(productVariantId))
                .findFirst();
    }

    private CartItem getItem(CartItemId itemId) {
        Objects.requireNonNull(itemId, "itemId must not be null");
        return items.stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found: " + itemId.getValue()));
    }

    private void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public static final class Builder {
        private CartId cartId;
        private CartOwnerType ownerType;
        private UserId userId;
        private List<CartItem> items = List.of();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder cartId(CartId cartId) {
            this.cartId = cartId;
            return this;
        }

        public Builder ownerType(CartOwnerType ownerType) {
            this.ownerType = ownerType;
            return this;
        }

        public Builder userId(UserId userId) {
            this.userId = userId;
            return this;
        }

        public Builder items(List<CartItem> items) {
            this.items = items == null ? List.of() : List.copyOf(items);
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Cart build() {
            Objects.requireNonNull(ownerType, "ownerType must not be null");
            if (ownerType == CartOwnerType.USER) {
                Objects.requireNonNull(userId, "userId must not be null for user cart");
            } else if (userId != null) {
                throw new CartDomainException("Guest cart must not have a userId.");
            }
            return new Cart(this);
        }
    }
}
