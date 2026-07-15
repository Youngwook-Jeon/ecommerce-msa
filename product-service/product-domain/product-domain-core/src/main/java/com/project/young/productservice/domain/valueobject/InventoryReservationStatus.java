package com.project.young.productservice.domain.valueobject;

import com.project.young.productservice.domain.exception.InventoryDomainException;

public enum InventoryReservationStatus {
    ACTIVE,
    CONFIRMED,
    RELEASED,
    EXPIRED;

    public boolean isTerminal() {
        return this != ACTIVE;
    }

    public boolean canTransitionTo(InventoryReservationStatus target) {
        if (target == null) {
            return false;
        }
        if (this == target) {
            return true;
        }
        return this == ACTIVE && target.isTerminal();
    }

    public static InventoryReservationStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InventoryDomainException("Inventory reservation status cannot be null or blank.");
        }
        try {
            return InventoryReservationStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InventoryDomainException("Unknown inventory reservation status: " + value);
        }
    }
}
