CREATE TABLE order_payment_reconciliation_failures
(
    order_id         UUID PRIMARY KEY,
    user_id          VARCHAR(36)  NOT NULL,
    payment_id       UUID         NOT NULL,
    payment_status   VARCHAR(16)  NOT NULL,
    attempts         INTEGER      NOT NULL,
    handling_status  VARCHAR(16)  NOT NULL,
    last_error       VARCHAR(4096),
    first_failure_at TIMESTAMPTZ  NOT NULL,
    last_failure_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_order_payment_reconciliation_status
        CHECK (handling_status IN ('RETRYING', 'ESCALATED', 'RESOLVED'))
);

CREATE INDEX idx_order_payment_reconciliation_status
    ON order_payment_reconciliation_failures (handling_status, last_failure_at);
