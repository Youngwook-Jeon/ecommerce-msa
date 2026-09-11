ALTER TABLE provider_webhook_inbox
    ADD COLUMN outcome VARCHAR(32);

UPDATE provider_webhook_inbox
SET outcome = CASE WHEN success THEN 'SUCCEEDED' ELSE 'FINAL_FAILED' END
WHERE outcome IS NULL;

ALTER TABLE provider_webhook_inbox
    ALTER COLUMN outcome SET NOT NULL,
    ADD CONSTRAINT ck_provider_webhook_inbox_outcome
        CHECK (outcome IN ('SUCCEEDED', 'ATTEMPT_FAILED', 'FINAL_FAILED'));
