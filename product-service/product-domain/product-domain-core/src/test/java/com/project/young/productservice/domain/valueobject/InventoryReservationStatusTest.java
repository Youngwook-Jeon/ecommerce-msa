package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.InventoryDomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryReservationStatusTest {

    @Test
    @DisplayName("canTransitionTo: ACTIVE에서 terminal 상태로만 전이 가능")
    void canTransitionTo_fromActive() {
        assertThat(InventoryReservationStatus.ACTIVE.canTransitionTo(InventoryReservationStatus.CONFIRMED)).isTrue();
        assertThat(InventoryReservationStatus.ACTIVE.canTransitionTo(InventoryReservationStatus.RELEASED)).isTrue();
        assertThat(InventoryReservationStatus.ACTIVE.canTransitionTo(InventoryReservationStatus.EXPIRED)).isTrue();
        assertThat(InventoryReservationStatus.ACTIVE.canTransitionTo(InventoryReservationStatus.ACTIVE)).isTrue();
    }

    @Test
    @DisplayName("canTransitionTo: terminal 상태에서는 다른 상태로 전이 불가")
    void canTransitionTo_fromTerminal_false() {
        assertThat(InventoryReservationStatus.CONFIRMED.canTransitionTo(InventoryReservationStatus.RELEASED)).isFalse();
        assertThat(InventoryReservationStatus.RELEASED.canTransitionTo(InventoryReservationStatus.ACTIVE)).isFalse();
        assertThat(InventoryReservationStatus.EXPIRED.canTransitionTo(InventoryReservationStatus.CONFIRMED)).isFalse();
    }

    @Test
    @DisplayName("fromString: 알 수 없는 값이면 예외")
    void fromString_unknown_throws() {
        assertThatThrownBy(() -> InventoryReservationStatus.fromString("BOGUS"))
                .isInstanceOf(InventoryDomainException.class)
                .hasMessageContaining("Unknown");
    }
}
