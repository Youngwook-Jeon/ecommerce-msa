package com.project.young.productservice.domain.inventory;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.exception.InsufficientInventoryException;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryAvailabilityTest {

    private static final ProductVariantId VARIANT_ID = new ProductVariantId(UUID.randomUUID());

    @Test
    @DisplayName("available: onHand - activeReserved (하한 0)")
    void available_subtractsActiveHolds() {
        assertThat(InventoryAvailability.available(10, 3)).isEqualTo(7);
        assertThat(InventoryAvailability.available(2, 5)).isEqualTo(0);
    }

    @Test
    @DisplayName("assertSufficient: 요청량이 available 이하면 통과")
    void assertSufficient_ok() {
        InventoryAvailability.assertSufficient(VARIANT_ID, 10, 3, 7);
    }

    @Test
    @DisplayName("assertSufficient: 요청량이 available 초과면 InsufficientInventoryException")
    void assertSufficient_insufficient_throws() {
        assertThatThrownBy(() -> InventoryAvailability.assertSufficient(VARIANT_ID, 10, 8, 3))
                .isInstanceOf(InsufficientInventoryException.class)
                .hasMessageContaining("available=2")
                .hasMessageContaining("requested=3");
    }

    @Test
    @DisplayName("assertOnHandCoversActiveHolds: onHand가 활성 예약보다 작으면 예외")
    void assertOnHandCoversActiveHolds_below_throws() {
        assertThatThrownBy(() -> InventoryAvailability.assertOnHandCoversActiveHolds(2, 5))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("active reservations");
    }
}
