-- V5: Manual compensation queue for payment.completed DLT (ops + future auto-refund)

CREATE TABLE saga_compensation
(
    id                       UUID PRIMARY KEY        DEFAULT uuidv7(),
    event_id                 UUID                    NOT NULL UNIQUE,
    payment_id               UUID,
    order_id                 UUID                    NOT NULL,
    user_id                  VARCHAR(36),
    amount                   VARCHAR(32),
    currency                 VARCHAR(3),
    source_topic             VARCHAR(128)            NOT NULL,
    dlt_topic                VARCHAR(128)            NOT NULL,
    source_partition         INT,
    source_offset            BIGINT,
    failure_exception_class  VARCHAR(512),
    failure_message          VARCHAR(2000),
    -- Policy encoding for future automation (this slice always persists handling_status=MANUAL).
    -- recommended_action: REPLAY | REFUND | MANUAL
    -- refund_sla: IMMEDIATE | NONE  (IMMEDIATE when REFUND; DLT → auto-refund when enabled later)
    recommended_action       VARCHAR(32)             NOT NULL,
    refund_sla               VARCHAR(32)             NOT NULL,
    classification_reason    VARCHAR(512)            NOT NULL,
    handling_status          VARCHAR(32)             NOT NULL DEFAULT 'MANUAL',
    created_at               TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_saga_compensation_recommended_action
        CHECK (recommended_action IN ('REPLAY', 'REFUND', 'MANUAL')),
    CONSTRAINT ck_saga_compensation_refund_sla
        CHECK (refund_sla IN ('IMMEDIATE', 'NONE')),
    CONSTRAINT ck_saga_compensation_handling_status
        CHECK (handling_status IN ('MANUAL', 'REPLAYED', 'REFUNDED', 'CLOSED'))
);

COMMENT ON TABLE saga_compensation IS
    'payment.completed DLT ingest; MANUAL rows for ops. Policy: inventory expiry → REFUND only; refund SLA → IMMEDIATE when auto-refund is enabled.';

CREATE INDEX idx_saga_compensation_order_id ON saga_compensation (order_id);
CREATE INDEX idx_saga_compensation_handling_status_created
    ON saga_compensation (handling_status, created_at);
