-- V2: Optimistic locking for user carts (see CartEntity @Version).
-- Guards concurrent aggregate mutations (e.g. guest-cart merge on login vs. add-to-cart).

ALTER TABLE carts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN carts.version IS
    'JPA @Version optimistic lock. Force-incremented on any cart mutation (including cart_items changes) so concurrent writers to the same cart conflict.';
