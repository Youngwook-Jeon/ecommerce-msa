ALTER TABLE order_payment_reconciliation_failures
    ADD COLUMN compensation_event_id UUID,
    ADD COLUMN resolution_reason VARCHAR(512),
    ADD COLUMN resolved_at TIMESTAMPTZ;

ALTER TABLE order_payment_reconciliation_failures
    DROP CONSTRAINT chk_order_payment_reconciliation_status;

ALTER TABLE order_payment_reconciliation_failures
    ADD CONSTRAINT chk_order_payment_reconciliation_status
        CHECK (handling_status IN ('RETRYING', 'ESCALATED', 'REFUND_REQUESTED', 'RESOLVED'));

CREATE UNIQUE INDEX uq_order_payment_reconciliation_compensation_event
    ON order_payment_reconciliation_failures (compensation_event_id)
    WHERE compensation_event_id IS NOT NULL;
