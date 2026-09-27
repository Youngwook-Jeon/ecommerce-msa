ALTER TABLE payment_refund_claims ADD COLUMN first_attempt_at TIMESTAMPTZ;

-- Existing claims may already have reached the PSP before this column existed.
UPDATE payment_refund_claims SET first_attempt_at = claimed_at;
