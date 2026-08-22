-- V3: External PSP session fields + webhook idempotency (Stripe-first, multi-provider ready)

ALTER TABLE payments
    ADD COLUMN provider VARCHAR(32),
    ADD COLUMN provider_payment_id VARCHAR(255),
    ADD COLUMN client_secret VARCHAR(512);

COMMENT ON COLUMN payments.provider IS 'Payment provider code (e.g. STUB, STRIPE)';
COMMENT ON COLUMN payments.provider_payment_id IS 'Provider-side payment id (e.g. Stripe PaymentIntent id)';
COMMENT ON COLUMN payments.client_secret IS 'Client secret for Embedded Elements (nullable after capture)';

CREATE UNIQUE INDEX uk_payments_provider_payment_id
    ON payments (provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

CREATE TABLE payment_provider_events
(
    event_id     VARCHAR(255) PRIMARY KEY,
    payment_id   UUID         NOT NULL REFERENCES payments (id),
    provider     VARCHAR(32)  NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE payment_provider_events IS 'Idempotency store for provider webhooks (Stripe event id, etc.)';

CREATE INDEX idx_payment_provider_events_payment_id ON payment_provider_events (payment_id);
