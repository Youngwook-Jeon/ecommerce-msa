ALTER TABLE payment_outbox ADD COLUMN refund_id UUID;

CREATE TABLE customer_refund_processings (
    refund_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL,
    order_id UUID NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
