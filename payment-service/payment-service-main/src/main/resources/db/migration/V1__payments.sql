-- V1: Payment aggregate (schema: payments)

CREATE TABLE payments
(
    id              UUID PRIMARY KEY        DEFAULT uuidv7(),
    order_id        UUID                    NOT NULL,
    user_id         VARCHAR(36)             NOT NULL,
    amount          DECIMAL(12, 2)          NOT NULL,
    currency        VARCHAR(3)              NOT NULL DEFAULT 'KRW',
    status          VARCHAR(32)             NOT NULL,
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT ck_payments_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

COMMENT ON TABLE payments IS 'Payment aggregate for order-payment saga';
COMMENT ON COLUMN payments.status IS 'PENDING while processing stub payment; terminal states COMPLETED or FAILED';
COMMENT ON COLUMN payments.failure_reason IS 'Populated when status is FAILED';

CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_payments_status ON payments (status);
