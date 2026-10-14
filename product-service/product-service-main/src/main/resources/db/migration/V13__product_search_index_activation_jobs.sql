CREATE TABLE product_search_index_activation_jobs (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    index_version_id UUID NOT NULL REFERENCES product_search_index_versions(id),
    expected_active_version_id UUID REFERENCES product_search_index_versions(id),
    alias_name VARCHAR(255) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    switch_started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    lock_version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (requested_by, idempotency_key)
);

-- Unconfirmed switches still occupy the global activation slot.
CREATE UNIQUE INDEX uq_product_search_index_unfinished_activation ON product_search_index_activation_jobs ((true))
    WHERE status IN ('QUEUED','RUNNING','SWITCHING','UNCONFIRMED');
CREATE INDEX idx_product_search_index_activation_jobs_created ON product_search_index_activation_jobs (created_at DESC, id DESC);
