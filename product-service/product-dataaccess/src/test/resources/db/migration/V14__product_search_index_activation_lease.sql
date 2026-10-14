ALTER TABLE product_search_index_activation_jobs ADD COLUMN owner_token UUID;
ALTER TABLE product_search_index_activation_jobs ADD COLUMN lease_until TIMESTAMPTZ;
CREATE INDEX idx_product_search_index_activation_expired ON product_search_index_activation_jobs (lease_until)
    WHERE status IN ('RUNNING','SWITCHING');
