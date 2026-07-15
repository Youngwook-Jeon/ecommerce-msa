package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryReservationTest {

    private static final Instant NOW = Instant.parse("2026-07-14T12:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-07-14T12:15:00Z");

    @Test
    @DisplayName("createActive: ACTIVE 예약을 생성한다")
    void createActive_success() {
        InventoryReservation reservation = activeReservation();

        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.ACTIVE);
        assertThat(reservation.getQuantity()).isEqualTo(2);
        assertThat(reservation.isActiveAt(NOW)).isTrue();
        assertThat(reservation.isActiveAt(EXPIRES)).isFalse();
    }

    @Test
    @DisplayName("createActive: expiresAt이 과거/현재면 예외")
    void createActive_expiresNotFuture_throws() {
        assertThatThrownBy(() -> InventoryReservation.createActive(
                new InventoryReservationId(UUID.randomUUID()),
                new CheckoutId(UUID.randomUUID()),
                new ProductVariantId(UUID.randomUUID()),
                1,
                NOW,
                NOW
        )).isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("expiresAt");
    }

    @Test
    @DisplayName("Builder: 필수 필드가 없으면 예외")
    void builder_missingRequired_throws() {
        assertThatThrownBy(() -> InventoryReservation.builder()
                .checkoutId(new CheckoutId(UUID.randomUUID()))
                .productVariantId(new ProductVariantId(UUID.randomUUID()))
                .quantity(1)
                .status(InventoryReservationStatus.ACTIVE)
                .expiresAt(EXPIRES)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build())
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("id cannot be null");
    }

    @Test
    @DisplayName("Builder: quantity가 0 이하면 예외")
    void builder_nonPositiveQuantity_throws() {
        assertThatThrownBy(() -> InventoryReservation.builder()
                .id(new InventoryReservationId(UUID.randomUUID()))
                .checkoutId(new CheckoutId(UUID.randomUUID()))
                .productVariantId(new ProductVariantId(UUID.randomUUID()))
                .quantity(0)
                .status(InventoryReservationStatus.ACTIVE)
                .expiresAt(EXPIRES)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build())
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    @DisplayName("confirm: ACTIVE → CONFIRMED")
    void confirm_fromActive() {
        InventoryReservation reservation = activeReservation();
        Instant confirmedAt = NOW.plusSeconds(30);

        reservation.confirm(confirmedAt);

        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.CONFIRMED);
        assertThat(reservation.getUpdatedAt()).isEqualTo(confirmedAt);
        assertThat(reservation.isActiveAt(confirmedAt)).isFalse();
    }

    @Test
    @DisplayName("release: ACTIVE → RELEASED")
    void release_fromActive() {
        InventoryReservation reservation = activeReservation();
        reservation.release(NOW.plusSeconds(10));
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("expire: ACTIVE → EXPIRED")
    void expire_fromActive() {
        InventoryReservation reservation = activeReservation();
        reservation.expire(EXPIRES);
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("confirm: 이미 CONFIRMED면 no-op")
    void confirm_idempotent() {
        InventoryReservation reservation = activeReservation();
        reservation.confirm(NOW.plusSeconds(1));
        reservation.confirm(NOW.plusSeconds(2));
        assertThat(reservation.getStatus()).isEqualTo(InventoryReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("confirm: RELEASED에서는 예외")
    void confirm_fromReleased_throws() {
        InventoryReservation reservation = activeReservation();
        reservation.release(NOW.plusSeconds(1));

        assertThatThrownBy(() -> reservation.confirm(NOW.plusSeconds(2)))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Cannot transition");
    }

    private static InventoryReservation activeReservation() {
        return InventoryReservation.createActive(
                new InventoryReservationId(UUID.randomUUID()),
                new CheckoutId(UUID.randomUUID()),
                new ProductVariantId(UUID.randomUUID()),
                2,
                EXPIRES,
                NOW
        );
    }
}
