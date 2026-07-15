package com.project.young.productservice.application.dto.result;

import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.entity.InventoryReservation;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReserveInventoryResultTest {

    @Test
    @DisplayName("from: domain reservation 목록을 result line으로 매핑한다")
    void from_mapsReservationLines() {
        UUID checkoutId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-07-15T10:15:00Z");
        Instant now = Instant.parse("2026-07-15T10:00:00Z");
        InventoryReservation reservation = InventoryReservation.reconstitute(
                new InventoryReservationId(reservationId),
                new CheckoutId(checkoutId),
                new ProductVariantId(variantId),
                2,
                InventoryReservationStatus.ACTIVE,
                expiresAt,
                now,
                now
        );

        ReserveInventoryResult result = ReserveInventoryResult.from(
                checkoutId,
                expiresAt,
                List.of(reservation),
                false
        );

        assertThat(result.checkoutId()).isEqualTo(checkoutId);
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.reusedExisting()).isFalse();
        assertThat(result.lines()).containsExactly(
                new ReserveInventoryResult.Line(reservationId, variantId, 2, "ACTIVE")
        );
    }
}
