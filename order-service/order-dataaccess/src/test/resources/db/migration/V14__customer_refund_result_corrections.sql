ALTER TABLE customer_refunds ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE customer_refunds DROP CONSTRAINT chk_customer_refund_status;
ALTER TABLE customer_refunds
    ADD CONSTRAINT chk_customer_refund_status
        CHECK (status IN ('REQUESTED', 'COMPLETED', 'FAILED', 'FAILED_AFTER_COMPLETION', 'CLOSED'));
ALTER TABLE customer_refunds
    ADD COLUMN result_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE customer_refunds
    ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE customer_refunds
    ADD COLUMN failed_at TIMESTAMPTZ;
UPDATE customer_refunds
SET result_version = 1,
    completed_at   = updated_at
WHERE status = 'COMPLETED';
UPDATE customer_refunds
SET result_version = 2,
    failed_at      = updated_at
WHERE status = 'FAILED';
