CREATE TABLE inventory_release_requested_outbox
(
    id                    UUID PRIMARY KEY        DEFAULT uuidv7(),
    compensation_event_id UUID                    NOT NULL UNIQUE,
    order_id              UUID                    NOT NULL,
    reason                VARCHAR(512)            NOT NULL,
    occurred_at           TIMESTAMPTZ             NOT NULL,
    created_at            TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE inventory_release_requested_outbox IS
    'Transactional outbox; Debezium relays inventory.release.requested for payment.failed compensation.';

CREATE INDEX idx_inventory_release_requested_outbox_order_id
    ON inventory_release_requested_outbox (order_id);
