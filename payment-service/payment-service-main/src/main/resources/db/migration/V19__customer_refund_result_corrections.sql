ALTER TABLE payment_refund_claims ADD COLUMN provider_refund_succeeded_at TIMESTAMPTZ;
ALTER TABLE payment_refund_claims ADD COLUMN provider_refund_failed_at TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_payment_refund_claim_provider_id
    ON payment_refund_claims (provider_refund_id) WHERE provider_refund_id IS NOT NULL;

UPDATE payment_refund_claims c SET provider_refund_succeeded_at = p.processed_at
FROM customer_refund_processings p
WHERE c.request_kind = 'CUSTOMER' AND c.request_id = p.refund_id;

UPDATE payment_refund_claims SET provider_refund_succeeded_at =
    COALESCE(provider_refund_checked_at, first_attempt_at, claimed_at)
    WHERE request_kind = 'CUSTOMER' AND provider_refund_state = 'SUCCEEDED'
      AND provider_refund_succeeded_at IS NULL;
UPDATE payment_refund_claims SET provider_refund_failed_at =
    COALESCE(provider_refund_checked_at, first_attempt_at, claimed_at)
    WHERE request_kind = 'CUSTOMER' AND provider_refund_state = 'FAILED';
CREATE INDEX idx_customer_refund_recent_successes
    ON payment_refund_claims (provider_refund_checked_at, payment_id)
    WHERE request_kind = 'CUSTOMER' AND provider_refund_state = 'SUCCEEDED';

ALTER TABLE customer_refund_reviews ADD COLUMN review_reason VARCHAR(32) NOT NULL DEFAULT 'UNCERTAIN_RESULT'
    CHECK (review_reason IN ('UNCERTAIN_RESULT', 'CONFIRMED_LATE_FAILURE'));
ALTER TABLE customer_refund_reviews ADD COLUMN confirmed_failure_reason VARCHAR(500);
ALTER TABLE customer_refund_reviews ADD COLUMN confirmed_failed_at TIMESTAMPTZ;

ALTER TABLE payment_outbox ADD COLUMN result_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_outbox ADD COLUMN failed_after_completion BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payment_outbox ADD COLUMN refund_completed_at TIMESTAMPTZ;
ALTER TABLE payment_outbox ADD COLUMN refund_failed_at TIMESTAMPTZ;
UPDATE payment_outbox SET result_version = 1, refund_completed_at = occurred_at
    WHERE event_type = 'CUSTOMER_REFUND_COMPLETED';
UPDATE payment_outbox SET result_version = 2, refund_failed_at = occurred_at
    WHERE event_type = 'CUSTOMER_REFUND_FAILED';
