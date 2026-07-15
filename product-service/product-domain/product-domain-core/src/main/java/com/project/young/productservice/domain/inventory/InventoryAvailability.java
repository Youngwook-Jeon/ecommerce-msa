package com.project.young.productservice.domain.inventory;

import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.exception.InsufficientInventoryException;
import com.project.young.productservice.domain.exception.InventoryDomainException;

/**
 * Pure helpers for soft-hold available stock.
 * {@code available = onHand - activeReserved} (never negative).
 */
public final class InventoryAvailability {

    private InventoryAvailability() {
    }

    public static int available(int onHand, int activeReservedQuantity) {
        if (onHand < 0) {
            throw new InventoryDomainException("On-hand stock cannot be negative.");
        }
        if (activeReservedQuantity < 0) {
            throw new InventoryDomainException("Active reserved quantity cannot be negative.");
        }
        return Math.max(0, onHand - activeReservedQuantity);
    }

    public static void assertSufficient(
            ProductVariantId variantId,
            int onHand,
            int activeReservedQuantity,
            int requestedQuantity
    ) {
        if (requestedQuantity <= 0) {
            throw new InventoryDomainException("Requested quantity must be positive.");
        }
        int available = available(onHand, activeReservedQuantity);
        if (available < requestedQuantity) {
            throw new InsufficientInventoryException(
                    "Insufficient inventory for variant " + variantId.getValue()
                            + ": available=" + available
                            + ", requested=" + requestedQuantity);
        }
    }

    /**
     * Admin stock reductions must not go below units already soft-held.
     */
    public static void assertOnHandCoversActiveHolds(int newOnHand, int activeReservedQuantity) {
        if (newOnHand < 0) {
            throw new InventoryDomainException("On-hand stock cannot be negative.");
        }
        if (activeReservedQuantity < 0) {
            throw new InventoryDomainException("Active reserved quantity cannot be negative.");
        }
        if (newOnHand < activeReservedQuantity) {
            throw new InventoryDomainException(
                    "Cannot set on-hand stock below active reservations: onHand="
                            + newOnHand + ", activeReserved=" + activeReservedQuantity);
        }
    }
}
