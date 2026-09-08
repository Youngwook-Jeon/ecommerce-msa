ALTER TABLE payment_refund_compensation_dlts
    ADD CONSTRAINT chk_payment_refund_compensation_dlts_status
    CHECK (handling_status IN ('MANUAL', 'REPLAYING', 'RESOLVED', 'ESCALATED'));
