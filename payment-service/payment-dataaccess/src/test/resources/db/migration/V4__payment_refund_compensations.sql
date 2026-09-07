CREATE TABLE payment_refund_compensations
(
    compensation_event_id UUID        PRIMARY KEY,
    payment_id            UUID        NOT NULL REFERENCES payments (id),
    order_id              UUID        NOT NULL,
    processed_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE payment_refund_compensations IS
    'Successfully applied saga refund compensations; compensation_event_id is the idempotency key.';

CREATE INDEX idx_payment_refund_compensations_payment_id
    ON payment_refund_compensations (payment_id);
