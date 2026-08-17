-- V4: Transactional outbox for Debezium CDC relay (order.created)

CREATE TABLE order_outbox
(
    id            UUID PRIMARY KEY        DEFAULT uuidv7(),
    event_id      UUID                    NOT NULL UNIQUE,
    order_id      UUID                    NOT NULL REFERENCES orders (id),
    user_id       VARCHAR(36)             NOT NULL,
    total_amount  DECIMAL(12, 2)          NOT NULL,
    currency      VARCHAR(3)              NOT NULL,
    occurred_at   TIMESTAMPTZ             NOT NULL,
    published_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_outbox_total_amount_non_negative CHECK (total_amount >= 0)
);

COMMENT ON TABLE order_outbox IS 'Transactional outbox; Debezium relays to order.created';
COMMENT ON COLUMN order_outbox.currency IS 'ISO 4217 code; default USD at enqueue time';

CREATE INDEX idx_order_outbox_pending
    ON order_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_order_outbox_order_id ON order_outbox (order_id);
