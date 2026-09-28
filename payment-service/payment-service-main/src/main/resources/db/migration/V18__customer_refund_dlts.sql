CREATE TABLE customer_refund_dlts (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    dlt_topic VARCHAR(255) NOT NULL,
    dlt_partition INTEGER NOT NULL,
    dlt_offset BIGINT NOT NULL,
    message_key TEXT,
    payload TEXT,
    source_topic VARCHAR(255),
    source_partition INTEGER,
    source_offset BIGINT,
    exception_class TEXT,
    exception_message TEXT,
    handling_status VARCHAR(32) NOT NULL DEFAULT 'ESCALATED'
        CHECK (handling_status IN ('ESCALATED', 'REPLAY_REQUESTED', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (dlt_topic, dlt_partition, dlt_offset)
);
CREATE INDEX idx_customer_refund_dlts_operations ON customer_refund_dlts (handling_status, created_at, id);
