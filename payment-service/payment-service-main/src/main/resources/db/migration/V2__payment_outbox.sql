-- V2: Transactional outbox for Debezium CDC relay (payment.completed / payment.failed)

CREATE TABLE payment_outbox
(
    id              UUID PRIMARY KEY        DEFAULT uuidv7(),
    event_id        UUID                    NOT NULL UNIQUE,
    payment_id      UUID                    NOT NULL REFERENCES payments (id),
    order_id        UUID                    NOT NULL,
    user_id         VARCHAR(36)             NOT NULL,
    event_type      VARCHAR(50)             NOT NULL,
    amount          DECIMAL(12, 2)          NOT NULL,
    currency        VARCHAR(3),
    failure_reason  VARCHAR(500),
    occurred_at     TIMESTAMPTZ             NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_outbox_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_payment_outbox_event_type CHECK (event_type IN ('PAYMENT_COMPLETED', 'PAYMENT_FAILED'))
);

COMMENT ON TABLE payment_outbox IS 'Transactional outbox; Debezium relays to payment.completed or payment.failed by event_type';
COMMENT ON COLUMN payment_outbox.currency IS 'Required for PAYMENT_COMPLETED; null for PAYMENT_FAILED';
COMMENT ON COLUMN payment_outbox.failure_reason IS 'Required for PAYMENT_FAILED; null for PAYMENT_COMPLETED';

CREATE INDEX idx_payment_outbox_pending
    ON payment_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_payment_outbox_order_id ON payment_outbox (order_id);
