CREATE TABLE inventory_release_compensations
(
    compensation_event_id UUID        PRIMARY KEY,
    order_id              UUID        NOT NULL,
    processed_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inventory_release_compensations_order_id
    ON inventory_release_compensations (order_id);
