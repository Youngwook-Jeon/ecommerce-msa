CREATE TABLE provider_webhook_inbox
(
    event_id             VARCHAR(255) PRIMARY KEY,
    provider             VARCHAR(32)  NOT NULL,
    provider_payment_id  VARCHAR(255) NOT NULL,
    success              BOOLEAN      NOT NULL,
    failure_reason       VARCHAR(4096),
    status               VARCHAR(32)  NOT NULL,
    attempts             INTEGER      NOT NULL DEFAULT 0,
    processing_started_at TIMESTAMPTZ,
    next_retry_at        TIMESTAMPTZ  NOT NULL,
    last_failure_message VARCHAR(4096),
    received_at          TIMESTAMPTZ  NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_provider_webhook_inbox_status
        CHECK (status IN ('RECEIVED', 'WAITING_FOR_PAYMENT', 'PROCESSING', 'APPLIED', 'ESCALATED'))
);

CREATE INDEX idx_provider_webhook_inbox_status_next_retry_at
    ON provider_webhook_inbox (status, next_retry_at, received_at);
