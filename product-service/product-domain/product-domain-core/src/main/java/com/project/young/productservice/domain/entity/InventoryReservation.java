package com.project.young.productservice.domain.entity;

import com.project.young.common.domain.entity.AggregateRoot;
import com.project.young.common.domain.valueobject.CheckoutId;
import com.project.young.common.domain.valueobject.ProductVariantId;
import com.project.young.productservice.domain.exception.InventoryDomainException;
import com.project.young.productservice.domain.valueobject.InventoryReservationId;
import com.project.young.productservice.domain.valueobject.InventoryReservationStatus;

import java.time.Instant;
import java.util.Objects;

/**
 * Soft-hold reservation line for one variant within a checkout.
 * On-hand stock is unchanged until {@link #confirm()}.
 */
public class InventoryReservation extends AggregateRoot<InventoryReservationId> {

    private final CheckoutId checkoutId;
    private final ProductVariantId productVariantId;
    private final int quantity;
    private InventoryReservationStatus status;
    private final Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    private InventoryReservation(Builder builder) {
        super.setId(builder.id);
        this.checkoutId = builder.checkoutId;
        this.productVariantId = builder.productVariantId;
        this.quantity = builder.quantity;
        this.status = builder.status;
        this.expiresAt = builder.expiresAt;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    /**
     * !!! FOR PERSISTENCE MAPPING ONLY !!!
     * Reconstitutes an InventoryReservation from a persistent state.
     * Bypasses create-time validations; use {@link Builder} / {@link #createActive} for new instances.
     */
    private InventoryReservation(
            InventoryReservationId id,
            CheckoutId checkoutId,
            ProductVariantId productVariantId,
            int quantity,
            InventoryReservationStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        super.setId(id);
        this.checkoutId = checkoutId;
        this.productVariantId = productVariantId;
        this.quantity = quantity;
        this.status = status;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static InventoryReservation createActive(
            InventoryReservationId id,
            CheckoutId checkoutId,
            ProductVariantId productVariantId,
            int quantity,
            Instant expiresAt,
            Instant now
    ) {
        Objects.requireNonNull(now, "now must not be null");
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InventoryDomainException("Reservation expiresAt must be in the future.");
        }

        return builder()
                .id(id)
                .checkoutId(checkoutId)
                .productVariantId(productVariantId)
                .quantity(quantity)
                .status(InventoryReservationStatus.ACTIVE)
                .expiresAt(expiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Reconstitutes a reservation from persistence. Skips create-time validations.
     */
    public static InventoryReservation reconstitute(
            InventoryReservationId id,
            CheckoutId checkoutId,
            ProductVariantId productVariantId,
            int quantity,
            InventoryReservationStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new InventoryReservation(
                id,
                checkoutId,
                productVariantId,
                quantity,
                status,
                expiresAt,
                createdAt,
                updatedAt
        );
    }

    public boolean isActiveAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return status == InventoryReservationStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void confirm(Instant now) {
        transitionTo(InventoryReservationStatus.CONFIRMED, now);
    }

    public void release(Instant now) {
        transitionTo(InventoryReservationStatus.RELEASED, now);
    }

    public void expire(Instant now) {
        transitionTo(InventoryReservationStatus.EXPIRED, now);
    }

    private void transitionTo(InventoryReservationStatus target, Instant now) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(now, "now must not be null");
        if (status == target) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new InventoryDomainException(
                    "Cannot transition inventory reservation from " + status + " to " + target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public CheckoutId getCheckoutId() {
        return checkoutId;
    }

    public ProductVariantId getProductVariantId() {
        return productVariantId;
    }

    public int getQuantity() {
        return quantity;
    }

    public InventoryReservationStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new InventoryDomainException("Reservation quantity must be positive.");
        }
    }

    public static final class Builder {
        private InventoryReservationId id;
        private CheckoutId checkoutId;
        private ProductVariantId productVariantId;
        private int quantity;
        private InventoryReservationStatus status;
        private Instant expiresAt;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(InventoryReservationId id) {
            this.id = id;
            return this;
        }

        public Builder checkoutId(CheckoutId checkoutId) {
            this.checkoutId = checkoutId;
            return this;
        }

        public Builder productVariantId(ProductVariantId productVariantId) {
            this.productVariantId = productVariantId;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder status(InventoryReservationStatus status) {
            this.status = status;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public InventoryReservation build() {
            validate();
            return new InventoryReservation(this);
        }

        private void validate() {
            if (id == null) {
                throw new InventoryDomainException("Reservation id cannot be null.");
            }
            if (checkoutId == null) {
                throw new InventoryDomainException("Checkout id cannot be null.");
            }
            if (productVariantId == null) {
                throw new InventoryDomainException("Product variant id cannot be null.");
            }
            validateQuantity(quantity);
            if (status == null) {
                throw new InventoryDomainException("Reservation status cannot be null.");
            }
            if (expiresAt == null) {
                throw new InventoryDomainException("Reservation expiresAt cannot be null.");
            }
            if (createdAt == null) {
                throw new InventoryDomainException("Reservation createdAt cannot be null.");
            }
            if (updatedAt == null) {
                throw new InventoryDomainException("Reservation updatedAt cannot be null.");
            }
        }
    }
}
