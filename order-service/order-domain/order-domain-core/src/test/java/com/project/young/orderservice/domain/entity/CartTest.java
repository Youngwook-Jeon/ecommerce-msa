package com.project.young.orderservice.domain.entity;

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
import com.project.young.orderservice.domain.valueobject.CartItemOptionLine;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartTest {

    private static final UserId USER_ID = new UserId("keycloak-sub-1");
    private static final CartId CART_ID = new CartId(UUID.randomUUID());
    private static final ProductId PRODUCT_ID = new ProductId(UUID.randomUUID());
    private static final ProductVariantId VARIANT_ID = new ProductVariantId(UUID.randomUUID());

    @Test
    @DisplayName("addOrMergeItem: 새 라인을 추가한다")
    void addOrMergeItem_addsNewLine() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        CartItemSnapshot snapshot = sampleSnapshot("999.00");

        CartItem item = cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                snapshot,
                2,
                new CartItemId(UUID.randomUUID())
        );

        assertThat(cart.itemCount()).isEqualTo(1);
        assertThat(item.getQuantity()).isEqualTo(2);
        assertThat(item.getId()).isNotNull();
        assertThat(cart.subtotalAmount()).isEqualTo(new Money(new BigDecimal("1998.00")));
    }

    @Test
    @DisplayName("addOrMergeItem: 동일 variant는 수량을 병합하고 스냅샷을 갱신한다")
    void addOrMergeItem_mergesSameVariant() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        CartItemId firstItemId = new CartItemId(UUID.randomUUID());
        cart.addOrMergeItem(PRODUCT_ID, VARIANT_ID, sampleSnapshot("100.00"), 1, firstItemId);

        CartItemSnapshot updatedSnapshot = sampleSnapshot("120.00");
        cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                updatedSnapshot,
                2,
                new CartItemId(UUID.randomUUID())
        );

        assertThat(cart.itemCount()).isEqualTo(1);
        assertThat(cart.getItems().get(0).getId()).isEqualTo(firstItemId);
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(cart.getItems().get(0).getSnapshot().unitPrice()).isEqualTo(new Money(new BigDecimal("120.00")));
    }

    @Test
    @DisplayName("addOrMergeItem: 새 라인 추가 시 newItemId가 없으면 예외")
    void addOrMergeItem_requiresNewItemId() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);

        assertThatThrownBy(() -> cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("100.00"),
                1,
                null
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("reconcileWithCatalog: 가격 변경 시 PriceUpdated를 반환하고 스냅샷을 갱신한다")
    void reconcileWithCatalog_updatesPrice() {
        Cart cart = seededCartWithOneItem();
        CartItemId itemId = cart.getItems().get(0).getId();
        CartItemSnapshot updatedSnapshot = sampleSnapshot("1099.00");

        CartCatalogLineState catalog = CartCatalogLineState.available(updatedSnapshot, 10);
        CartSyncResult result = cart.reconcileWithCatalog(Map.of(itemId, catalog));

        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().get(0)).isInstanceOf(CartSyncChange.PriceUpdated.class);
        assertThat(cart.getItems().get(0).getSnapshot().unitPrice()).isEqualTo(new Money(new BigDecimal("1099.00")));
    }

    @Test
    @DisplayName("reconcileWithCatalog: 품절 라인은 제거하고 Removed 변경을 반환한다")
    void reconcileWithCatalog_removesUnavailableLine() {
        Cart cart = seededCartWithOneItem();
        CartItemId itemId = cart.getItems().get(0).getId();

        CartSyncResult result = cart.reconcileWithCatalog(Map.of(
                itemId,
                CartCatalogLineState.unavailable(CartSyncRemovalReason.OUT_OF_STOCK)
        ));

        assertThat(cart.isEmpty()).isTrue();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().get(0)).isInstanceOf(CartSyncChange.Removed.class);
    }

    @Test
    @DisplayName("reconcileWithCatalog: 재고보다 많은 수량은 재고 수량으로 조정한다")
    void reconcileWithCatalog_clampsQuantityToStock() {
        Cart cart = seededCartWithOneItem();
        CartItemId itemId = cart.getItems().get(0).getId();
        cart.updateItemQuantity(itemId, 5);

        CartCatalogLineState catalog = CartCatalogLineState.available(sampleSnapshot("999.00"), 2);
        CartSyncResult result = cart.reconcileWithCatalog(Map.of(itemId, catalog));

        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.changes()).anyMatch(CartSyncChange.QuantityAdjusted.class::isInstance);
    }

    @Test
    @DisplayName("updateItemQuantity: 없는 itemId면 CartItemNotFoundException")
    void updateItemQuantity_missingItem() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);

        assertThatThrownBy(() -> cart.updateItemQuantity(new CartItemId(UUID.randomUUID()), 1))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    @DisplayName("addOrMergeItem: MAX_LINE_ITEMS 초과 시 예외")
    void addOrMergeItem_exceedsMaxLines() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        for (int i = 0; i < Cart.MAX_LINE_ITEMS; i++) {
            cart.addOrMergeItem(
                    PRODUCT_ID,
                    new ProductVariantId(UUID.randomUUID()),
                    sampleSnapshot("10.00"),
                    1,
                    new CartItemId(UUID.randomUUID())
            );
        }

        assertThatThrownBy(() -> cart.addOrMergeItem(
                PRODUCT_ID,
                new ProductVariantId(UUID.randomUUID()),
                sampleSnapshot("10.00"),
                1,
                new CartItemId(UUID.randomUUID())
        )).isInstanceOf(CartDomainException.class);
    }

    private Cart seededCartWithOneItem() {
        Cart cart = Cart.createForUser(USER_ID, CART_ID);
        cart.addOrMergeItem(
                PRODUCT_ID,
                VARIANT_ID,
                sampleSnapshot("999.00"),
                1,
                new CartItemId(UUID.randomUUID())
        );
        return cart;
    }

    private CartItemSnapshot sampleSnapshot(String price) {
        return new CartItemSnapshot(
                "iPhone 15 Pro",
                "Apple",
                "SKU-001",
                "https://cdn.example.com/image.jpg",
                new Money(new BigDecimal(price)),
                List.of(new CartItemOptionLine(
                        1,
                        UUID.randomUUID(),
                        "Color",
                        UUID.randomUUID(),
                        "Black"
                ))
        );
    }
}
