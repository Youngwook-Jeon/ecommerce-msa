CREATE TABLE product_search_index_reconciliation_attempts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    job_id UUID NOT NULL REFERENCES product_search_index_activation_jobs(id),
    requested_by VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    observation VARCHAR(32),
    observed_indices JSONB,
    alias_plain BOOLEAN,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    lock_version BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_product_search_index_running_reconciliation
    ON product_search_index_reconciliation_attempts (job_id) WHERE finished_at IS NULL;
CREATE INDEX idx_product_search_index_reconciliation_latest
    ON product_search_index_reconciliation_attempts (job_id, created_at DESC, id DESC);
