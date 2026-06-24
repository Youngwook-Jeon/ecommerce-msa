package com.project.young.orderservice.application.mapper;

import com.project.young.orderservice.application.port.output.view.CartCatalogLineView;
import com.project.young.orderservice.application.port.output.view.CartCatalogOptionLineView;
import com.project.young.orderservice.domain.sync.CartCatalogLineState;
import com.project.young.orderservice.domain.sync.CartSyncRemovalReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
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

    private CartCatalogLineView catalogView(boolean purchasable, int stockQuantity) {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
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
