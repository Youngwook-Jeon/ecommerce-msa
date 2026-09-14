CREATE TABLE payment_order_created_dlts
(
    event_id                UUID PRIMARY KEY,
    order_id                UUID         NOT NULL,
    user_id                 VARCHAR(36)  NOT NULL,
    total_amount            VARCHAR(32)  NOT NULL,
    currency                VARCHAR(3)   NOT NULL,
    source_topic            VARCHAR(255) NOT NULL,
    dlt_topic               VARCHAR(255) NOT NULL,
    source_partition        INTEGER,
    source_offset           BIGINT,
    failure_exception_class VARCHAR(1024),
    failure_message         VARCHAR(4096),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handling_status         VARCHAR(32)  NOT NULL DEFAULT 'MANUAL',
    replay_started_at       TIMESTAMPTZ,
    replay_attempts         INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT chk_payment_order_created_dlts_status
        CHECK (handling_status IN ('MANUAL', 'REPLAYING', 'RESOLVED', 'ESCALATED'))
);

CREATE INDEX idx_payment_order_created_dlts_status
    ON payment_order_created_dlts (handling_status, created_at);
CREATE INDEX idx_payment_order_created_dlts_order_id
    ON payment_order_created_dlts (order_id);
