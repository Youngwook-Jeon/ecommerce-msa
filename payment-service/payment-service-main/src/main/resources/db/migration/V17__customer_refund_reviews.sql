CREATE TABLE customer_refund_reviews (
    refund_id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments (id),
    provider_refund_id VARCHAR(255),
    handling_status VARCHAR(32) NOT NULL DEFAULT 'ESCALATED',
    failure_exception_class VARCHAR(1024) NOT NULL,
    failure_message VARCHAR(4096),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_refund_review_status CHECK (handling_status = 'ESCALATED')
);
CREATE INDEX idx_customer_refund_reviews_created_at ON customer_refund_reviews (created_at, refund_id);
COMMENT ON TABLE customer_refund_reviews IS 'Customer-only operational review queue; unknown outcomes are not refund failures';
