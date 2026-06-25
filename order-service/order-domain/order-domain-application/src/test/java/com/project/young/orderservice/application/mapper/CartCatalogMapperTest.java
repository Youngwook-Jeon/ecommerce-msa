package com.project.young.orderservice.application.mapper;

import com.project.young.common.domain.valueobject.Money;
import com.project.young.common.domain.valueobject.ProductId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
import com.project.young.orderservice.application.port.output.view.CartCatalogOptionLineView;
import com.project.young.orderservice.domain.entity.Cart;
import com.project.young.orderservice.domain.entity.CartItem;
import com.project.young.orderservice.domain.sync.CartCatalogLineState;
import com.project.young.orderservice.domain.sync.CartSyncRemovalReason;
import com.project.young.orderservice.domain.valueobject.CartId;
import com.project.young.orderservice.domain.valueobject.CartItemId;
import com.project.young.orderservice.domain.valueobject.CartItemSnapshot;
import com.project.young.orderservice.domain.valueobject.CartOwnerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartCatalogMapperTest {

    @Test
    @DisplayName("toLineState: purchasable=false면 NOT_PURCHASABLE")
    void toLineState_notPurchasable() {
        CartCatalogLineView view = catalogView(false, 5);

        CartCatalogLineState state = CartCatalogMapper.toLineState(view);

        assertThat(state.available()).isFalse();
        assertThat(state.removalReason()).isEqualTo(CartSyncRemovalReason.NOT_PURCHASABLE);
    }

    @Test
    @DisplayName("toLineState: 재고 0이면 OUT_OF_STOCK")
    void toLineState_outOfStock() {
        CartCatalogLineView view = catalogView(true, 0);

        CartCatalogLineState state = CartCatalogMapper.toLineState(view);

        assertThat(state.available()).isFalse();
        assertThat(state.removalReason()).isEqualTo(CartSyncRemovalReason.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("toLineState: 정상 라인은 snapshot과 stock을 포함한다")
    void toLineState_available() {
        CartCatalogLineView view = catalogView(true, 3);

        CartCatalogLineState state = CartCatalogMapper.toLineState(view);

        assertThat(state.available()).isTrue();
        assertThat(state.stockQuantity()).isEqualTo(3);
        assertThat(state.snapshot().productName()).isEqualTo("Phone");
        assertThat(state.snapshot().variantOptions()).hasSize(1);
    }

    @Test
    @DisplayName("toLineStateByItemId: resolved map을 cart item id 기준 catalog state로 변환한다")
    void toLineStateByItemId_mapsResolvedViews() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID missingVariantId = UUID.randomUUID();
        CartItemId foundItemId = new CartItemId(UUID.randomUUID());
        CartItemId missingItemId = new CartItemId(UUID.randomUUID());

        Cart cart = Cart.builder()
                .cartId(new CartId(UUID.randomUUID()))
                .ownerType(CartOwnerType.GUEST)
                .items(List.of(
                        CartItem.reconstitute(
                                foundItemId,
                                new ProductId(productId),
                                new ProductVariantId(variantId),
                                new CartItemSnapshot(
                                        "Old", "Brand", "SKU", null,
                                        new Money(new BigDecimal("1.00")),
                                        List.of()
                                ),
                                1
                        ),
                        CartItem.reconstitute(
                                missingItemId,
                                new ProductId(productId),
                                new ProductVariantId(missingVariantId),
                                new CartItemSnapshot(
                                        "Gone", "Brand", "SKU", null,
                                        new Money(new BigDecimal("1.00")),
                                        List.of()
                                ),
                                1
                        )
                ))
                .build();

        Map<UUID, CartCatalogLineView> resolved = Map.of(variantId, catalogView(true, 3, productId, variantId));

        Map<CartItemId, CartCatalogLineState> states = CartCatalogMapper.toLineStateByItemId(cart, resolved);

        assertThat(states.get(foundItemId).available()).isTrue();
        assertThat(states.get(foundItemId).stockQuantity()).isEqualTo(3);
        assertThat(states.get(missingItemId).available()).isFalse();
        assertThat(states.get(missingItemId).removalReason()).isEqualTo(CartSyncRemovalReason.VARIANT_NOT_FOUND);
    }

    private CartCatalogLineView catalogView(boolean purchasable, int stockQuantity) {
        return catalogView(purchasable, stockQuantity, UUID.randomUUID(), UUID.randomUUID());
    }

    private CartCatalogLineView catalogView(
            boolean purchasable,
            int stockQuantity,
            UUID productId,
            UUID variantId
    ) {
        return new CartCatalogLineView(
                productId,
                variantId,
                "Phone",
                "Brand",
                "SKU",
                "https://img",
                new BigDecimal("100.00"),
                purchasable,
                stockQuantity,
                List.of(new CartCatalogOptionLineView(
                        1,
                        UUID.randomUUID(),
                        "Color",
                        UUID.randomUUID(),
                        "Black"
                ))
        );
    }
}
