-- Root information edits use optimistic version checks; existing groups start at version 0.
ALTER TABLE option_groups
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
