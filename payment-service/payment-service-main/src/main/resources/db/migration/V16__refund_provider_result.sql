ALTER TABLE payment_refund_claims ADD COLUMN provider_refund_id VARCHAR(255);
ALTER TABLE payment_refund_claims ADD COLUMN provider_refund_state VARCHAR(32);
ALTER TABLE payment_refund_claims ADD COLUMN provider_refund_checked_at TIMESTAMPTZ;

CREATE INDEX idx_payment_refund_claims_pending
    ON payment_refund_claims (provider_refund_checked_at, payment_id)
    WHERE provider_refund_state = 'PENDING';

ALTER TABLE payment_outbox DROP CONSTRAINT ck_payment_outbox_event_type;
ALTER TABLE payment_outbox ADD CONSTRAINT ck_payment_outbox_event_type
    CHECK (event_type IN ('PAYMENT_COMPLETED', 'PAYMENT_FAILED',
                         'CUSTOMER_REFUND_COMPLETED', 'CUSTOMER_REFUND_FAILED'));
CREATE UNIQUE INDEX uq_payment_outbox_customer_refund_failed
    ON payment_outbox (refund_id, event_type)
    WHERE event_type = 'CUSTOMER_REFUND_FAILED';
