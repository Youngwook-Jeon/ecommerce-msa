ALTER TABLE product_search_index_build_jobs
    ADD COLUMN owner_token UUID,
    ADD COLUMN lease_until TIMESTAMPTZ;

CREATE INDEX idx_product_search_index_build_lease ON product_search_index_build_jobs (lease_until)
    WHERE status IN ('RUNNING', 'VERIFYING');
