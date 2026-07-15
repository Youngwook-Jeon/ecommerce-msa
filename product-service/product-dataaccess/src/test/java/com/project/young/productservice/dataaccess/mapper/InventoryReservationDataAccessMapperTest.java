package com.project.young.productservice.dataaccess.mapper;

import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.dataaccess.entity.InventoryReservationEntity;
import com.project.young.productservice.domain.entity.InventoryReservation;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryReservationDataAccessMapperTest {

    private final InventoryReservationDataAccessMapper mapper = new InventoryReservationDataAccessMapper();

    @Test
    @DisplayName("toEntity/toDomain: reservation의 모든 영속 필드를 왕복 매핑한다")
    void mapsAllPersistenceFieldsRoundTrip() {
        UUID reservationId = UUID.randomUUID();
        UUID checkoutId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-15T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-07-15T10:01:00Z");
        Instant expiresAt = Instant.parse("2026-07-15T10:15:00Z");
        InventoryReservation reservation = InventoryReservation.reconstitute(
                new InventoryReservationId(reservationId),
                new CheckoutId(checkoutId),
                new ProductVariantId(variantId),
                3,
                InventoryReservationStatus.ACTIVE,
                expiresAt,
                createdAt,
                updatedAt
        );

        InventoryReservationEntity entity = mapper.toEntity(reservation);
        InventoryReservation restored = mapper.toDomain(entity);

        assertThat(entity.getId()).isEqualTo(reservationId);
        assertThat(entity.getCheckoutId()).isEqualTo(checkoutId);
        assertThat(entity.getProductVariantId()).isEqualTo(variantId);
        assertThat(entity.getQuantity()).isEqualTo(3);
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(updatedAt);

        assertThat(restored.getId().getValue()).isEqualTo(reservationId);
        assertThat(restored.getCheckoutId().getValue()).isEqualTo(checkoutId);
        assertThat(restored.getProductVariantId().getValue()).isEqualTo(variantId);
        assertThat(restored.getQuantity()).isEqualTo(3);
        assertThat(restored.getStatus()).isEqualTo(InventoryReservationStatus.ACTIVE);
        assertThat(restored.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(restored.getCreatedAt()).isEqualTo(createdAt);
        assertThat(restored.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("updateEntity: 변경 가능한 status와 updatedAt만 갱신한다")
    void updateEntityUpdatesMutableFieldsOnly() {
        UUID reservationId = UUID.randomUUID();
        UUID checkoutId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-15T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-07-15T10:15:00Z");
        Instant confirmedAt = Instant.parse("2026-07-15T10:05:00Z");
        InventoryReservation reservation = InventoryReservation.reconstitute(
                new InventoryReservationId(reservationId),
                new CheckoutId(checkoutId),
                new ProductVariantId(variantId),
                2,
                InventoryReservationStatus.CONFIRMED,
                expiresAt,
                createdAt,
                confirmedAt
        );
        InventoryReservationEntity entity = InventoryReservationEntity.builder()
                .id(reservationId)
                .checkoutId(checkoutId)
                .productVariantId(variantId)
                .quantity(2)
                .status("ACTIVE")
                .expiresAt(expiresAt)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();

        mapper.updateEntity(reservation, entity);

        assertThat(entity.getStatus()).isEqualTo("CONFIRMED");
        assertThat(entity.getUpdatedAt()).isEqualTo(confirmedAt);
        assertThat(entity.getCheckoutId()).isEqualTo(checkoutId);
        assertThat(entity.getProductVariantId()).isEqualTo(variantId);
        assertThat(entity.getQuantity()).isEqualTo(2);
        assertThat(entity.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(entity.getCreatedAt()).isEqualTo(createdAt);
    }
}
