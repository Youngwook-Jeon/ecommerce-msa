CREATE TABLE payment_refund_compensation_dlts (
    compensation_event_id UUID PRIMARY KEY, payment_id UUID NOT NULL, order_id UUID NOT NULL,
    source_topic VARCHAR(255) NOT NULL, dlt_topic VARCHAR(255) NOT NULL, source_partition INTEGER, source_offset BIGINT,
    failure_exception_class VARCHAR(1024), failure_message VARCHAR(4096), created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payment_refund_compensation_dlts_order_id ON payment_refund_compensation_dlts (order_id);
