CREATE TABLE product_catalog_outbox
(
    id           UUID PRIMARY KEY     DEFAULT uuidv7(),
    event_id     UUID        NOT NULL UNIQUE,
    product_id   UUID        NOT NULL,
    category_id  BIGINT,
    change_type  VARCHAR(50) NOT NULL,
    occurred_at  TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_catalog_outbox_pending
    ON product_catalog_outbox (created_at)
    WHERE published_at IS NULL;
