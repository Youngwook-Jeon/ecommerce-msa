-- Allow re-reserve after RELEASED/EXPIRED for the same checkout_id + variant.
-- Only one ACTIVE hold per (checkout, variant) may exist at a time.

ALTER TABLE inventory_reservations
    DROP CONSTRAINT IF EXISTS uk_inventory_reservations_checkout_variant;

CREATE UNIQUE INDEX uk_inventory_reservations_active_checkout_variant
    ON inventory_reservations (checkout_id, product_variant_id)
    WHERE status = 'ACTIVE';
