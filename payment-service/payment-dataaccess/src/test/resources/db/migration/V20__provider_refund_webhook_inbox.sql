CREATE TABLE provider_refund_webhook_inbox (
    event_id VARCHAR(255) PRIMARY KEY,
    provider_refund_id VARCHAR(255) NOT NULL,
    provider_payment_id VARCHAR(255) NOT NULL,
    refund_state VARCHAR(32) NOT NULL CHECK (refund_state IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    failure_reason VARCHAR(500),
    status VARCHAR(32) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'PROCESSING', 'APPLIED', 'ESCALATED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMPTZ,
    last_error VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_provider_refund_webhook_inbox_ready
    ON provider_refund_webhook_inbox (status, next_attempt_at, event_id);
