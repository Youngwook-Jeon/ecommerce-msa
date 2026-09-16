CREATE TABLE order_payment_reconciliation_operation_audits
(
    id                    UUID PRIMARY KEY,
    order_id              UUID         NOT NULL,
    operator_id           VARCHAR(128) NOT NULL,
    request_id            UUID         NOT NULL,
    operation             VARCHAR(16)  NOT NULL,
    reason                VARCHAR(512),
    compensation_event_id UUID,
    occurred_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_order_payment_reconciliation_audit_operation
        CHECK (operation IN ('REPLAY', 'CLOSE', 'REFUND'))
);

CREATE INDEX idx_order_payment_reconciliation_audit_order_occurred
    ON order_payment_reconciliation_operation_audits (order_id, occurred_at DESC);
