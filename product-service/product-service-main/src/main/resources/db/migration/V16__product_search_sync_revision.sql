CREATE TABLE product_search_sync_state (
    product_id UUID PRIMARY KEY REFERENCES products(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL
);
INSERT INTO product_search_sync_state(product_id, revision) SELECT id, 1 FROM products;

CREATE TABLE product_catalog_change_history (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    event_id UUID NOT NULL UNIQUE,
    product_id UUID NOT NULL REFERENCES products(id),
    revision BIGINT NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    UNIQUE (product_id, revision)
);
CREATE INDEX idx_product_catalog_change_history_product ON product_catalog_change_history(product_id, occurred_at DESC, id DESC);
ALTER TABLE product_catalog_outbox ADD COLUMN search_revision BIGINT;
