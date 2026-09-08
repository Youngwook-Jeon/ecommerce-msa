ALTER TABLE payment_refund_compensation_dlts
    ADD COLUMN handling_status VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN replay_started_at TIMESTAMPTZ,
    ADD COLUMN replay_attempts INTEGER NOT NULL DEFAULT 0;
CREATE INDEX idx_payment_refund_compensation_dlts_status ON payment_refund_compensation_dlts (handling_status, created_at);
