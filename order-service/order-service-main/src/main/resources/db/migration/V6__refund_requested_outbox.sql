CREATE TABLE refund_requested_outbox (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    compensation_event_id UUID NOT NULL UNIQUE,
    payment_id UUID NOT NULL,
    order_id UUID NOT NULL,
    user_id VARCHAR(36),
    reason VARCHAR(512),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_refund_requested_outbox_payment_id ON refund_requested_outbox(payment_id);
