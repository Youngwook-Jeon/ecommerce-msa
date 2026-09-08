ALTER TABLE provider_session_requests
    ADD COLUMN processing_started_at TIMESTAMPTZ, ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE provider_session_requests DROP CONSTRAINT ck_provider_session_request_status;

ALTER TABLE provider_session_requests
    ADD CONSTRAINT ck_provider_session_request_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'ESCALATED'));
