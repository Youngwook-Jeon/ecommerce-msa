CREATE TABLE product_search_index_versions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    version_number BIGINT NOT NULL UNIQUE,
    index_name VARCHAR(255) NOT NULL UNIQUE,
    mapping_version INTEGER NOT NULL,
    mapping_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE SEQUENCE product_search_index_version_number_seq START WITH 1;
CREATE UNIQUE INDEX uq_product_search_index_active ON product_search_index_versions ((true)) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_product_search_index_activating ON product_search_index_versions ((true)) WHERE status = 'ACTIVATING';
CREATE INDEX idx_product_search_index_versions_created ON product_search_index_versions (created_at DESC, id DESC);

CREATE TABLE product_search_index_build_jobs (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    index_version_id UUID NOT NULL UNIQUE REFERENCES product_search_index_versions(id),
    requested_by VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    batch_size INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_product_count BIGINT,
    indexed_products BIGINT NOT NULL DEFAULT 0,
    completed_batches BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    lock_version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (requested_by, idempotency_key)
);

CREATE UNIQUE INDEX uq_product_search_index_unfinished_build ON product_search_index_build_jobs ((true))
    WHERE status IN ('QUEUED','RUNNING','VERIFYING');
