CREATE TABLE provider_session_requests
(
    payment_id      UUID PRIMARY KEY REFERENCES payments (id),
    status          VARCHAR(32) NOT NULL,
    failure_message VARCHAR(4096),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_provider_session_request_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED'))
);
CREATE INDEX idx_provider_session_requests_pending ON provider_session_requests (status, created_at);
