ALTER TABLE payment_outbox DROP CONSTRAINT ck_payment_outbox_event_type;
ALTER TABLE payment_outbox ADD CONSTRAINT ck_payment_outbox_event_type
    CHECK (event_type IN ('PAYMENT_COMPLETED', 'PAYMENT_FAILED', 'CUSTOMER_REFUND_COMPLETED'));

CREATE TABLE payment_refund_claims (
    payment_id UUID PRIMARY KEY REFERENCES payments (id),
    request_id UUID NOT NULL,
    request_kind VARCHAR(32) NOT NULL,
    claimed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_refund_claim_kind CHECK (request_kind IN ('COMPENSATION', 'CUSTOMER'))
);

-- Reserve payments refunded before the common claim was introduced.
INSERT INTO payment_refund_claims (payment_id, request_id, request_kind, claimed_at)
SELECT DISTINCT ON (payment_id) payment_id, request_id, request_kind, processed_at
FROM (
    SELECT payment_id, compensation_event_id AS request_id, 'COMPENSATION' AS request_kind, processed_at
    FROM payment_refund_compensations
    UNION ALL
    SELECT payment_id, refund_id AS request_id, 'CUSTOMER' AS request_kind, processed_at
    FROM customer_refund_processings
) processed
ORDER BY payment_id, processed_at, request_id;
