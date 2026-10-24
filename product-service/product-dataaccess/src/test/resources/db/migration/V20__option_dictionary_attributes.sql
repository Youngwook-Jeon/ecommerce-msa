-- Legacy dictionaries remain unlinked until their meaning is explicitly identified.
ALTER TABLE option_groups
    ADD COLUMN attribute_id UUID,
    ADD CONSTRAINT fk_option_group_attribute FOREIGN KEY (attribute_id)
        REFERENCES attribute_definitions (id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_option_group_attribute UNIQUE (attribute_id),
    ADD CONSTRAINT uk_option_group_id_attribute UNIQUE (id, attribute_id);

-- attribute_id is repeated to enforce ownership using composite foreign keys.
ALTER TABLE option_values
    ADD COLUMN attribute_id UUID,
    ADD COLUMN attribute_value_id UUID,
    ADD CONSTRAINT ck_option_value_attribute_mapping CHECK (
        (attribute_id IS NULL AND attribute_value_id IS NULL)
        OR (attribute_id IS NOT NULL AND attribute_value_id IS NOT NULL)
    ),
    ADD CONSTRAINT fk_option_value_group_attribute FOREIGN KEY (option_group_id, attribute_id)
        REFERENCES option_groups (id, attribute_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_option_value_attribute_value FOREIGN KEY (attribute_id, attribute_value_id)
        REFERENCES attribute_values (attribute_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_option_value_attribute_value UNIQUE (option_group_id, attribute_value_id);

CREATE INDEX idx_option_values_attribute_value ON option_values (attribute_value_id);
