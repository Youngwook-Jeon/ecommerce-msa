ALTER TABLE saga_compensation
    DROP CONSTRAINT ck_saga_compensation_recommended_action;

ALTER TABLE saga_compensation
    ADD CONSTRAINT ck_saga_compensation_recommended_action
        CHECK (recommended_action IN ('REPLAY', 'REFUND', 'RELEASE_INVENTORY', 'MANUAL'));
