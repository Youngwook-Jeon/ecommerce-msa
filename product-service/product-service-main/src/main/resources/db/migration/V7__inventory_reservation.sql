-- Soft-hold inventory reservations for checkout (Phase 2).
-- available = on_hand - SUM(ACTIVE reservations where expires_at > now())

ALTER TABLE product_variants
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN product_variants.version IS
    'JPA @Version optimistic lock. Force-incremented when creating soft-hold reservations so concurrent reserves on the same variant conflict.';

CREATE TABLE inventory_reservations
(
    id                 UUID PRIMARY KEY        DEFAULT uuidv7(),
    checkout_id        UUID           NOT NULL,
    product_variant_id UUID           NOT NULL REFERENCES product_variants (id),
    quantity           INTEGER        NOT NULL CHECK (quantity > 0),
    status             VARCHAR(20)    NOT NULL,
    expires_at         TIMESTAMPTZ    NOT NULL,
    version            INTEGER        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_reservations_status
        CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT uk_inventory_reservations_checkout_variant
        UNIQUE (checkout_id, product_variant_id)
);

CREATE INDEX idx_inv_res_expire
    ON inventory_reservations (status, expires_at)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_inv_res_variant_active
    ON inventory_reservations (product_variant_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_inv_res_checkout
    ON inventory_reservations (checkout_id);
