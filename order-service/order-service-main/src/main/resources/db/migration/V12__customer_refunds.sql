CREATE TABLE customer_refunds
(
    refund_id      UUID PRIMARY KEY        DEFAULT uuidv7(),
    order_id       UUID         NOT NULL UNIQUE,
    payment_id     UUID         NOT NULL,
    user_id        VARCHAR(36)  NOT NULL,
    reason         VARCHAR(512) NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    failure_reason VARCHAR(512),
    requested_at   TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_customer_refund_status CHECK (status IN ('REQUESTED', 'COMPLETED', 'FAILED', 'CLOSED'))
);

CREATE INDEX idx_customer_refunds_user_id_requested_at
    ON customer_refunds (user_id, requested_at DESC);

CREATE TABLE customer_refund_requested_outbox
(
    id          UUID PRIMARY KEY        DEFAULT uuidv7(),
    refund_id   UUID         NOT NULL UNIQUE,
    payment_id  UUID         NOT NULL,
    order_id    UUID         NOT NULL,
    user_id     VARCHAR(36)  NOT NULL,
    reason      VARCHAR(512) NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);
