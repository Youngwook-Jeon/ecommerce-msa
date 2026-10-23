-- Attribute codes, types and units are immutable after creation.
CREATE TABLE attribute_definitions (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    code VARCHAR(100) NOT NULL UNIQUE CHECK (code ~ '^[a-z][a-z0-9_]{0,99}$'),
    display_name VARCHAR(100) NOT NULL CHECK (length(btrim(display_name)) > 0),
    data_type VARCHAR(10) NOT NULL CHECK (data_type IN ('TEXT', 'ENUM', 'NUMBER', 'BOOLEAN', 'DATE')),
    canonical_unit VARCHAR(20),
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, data_type),
    CHECK (canonical_unit IS NULL OR (data_type = 'NUMBER' AND length(btrim(canonical_unit)) > 0))
);

CREATE TABLE attribute_values (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    attribute_id UUID NOT NULL,
    data_type VARCHAR(10) NOT NULL,
    display_name VARCHAR(100) NOT NULL CHECK (length(btrim(display_name)) > 0),
    text_value VARCHAR(100),
    numeric_value NUMERIC(19, 6),
    boolean_value BOOLEAN,
    date_value DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attribute_value_type FOREIGN KEY (attribute_id, data_type)
        REFERENCES attribute_definitions (id, data_type) ON DELETE RESTRICT,
    UNIQUE (attribute_id, id),
    CHECK (num_nonnulls(text_value, numeric_value, boolean_value, date_value) = 1),
    CHECK (
        (data_type IN ('TEXT', 'ENUM') AND text_value IS NOT NULL AND length(btrim(text_value)) > 0)
        OR (data_type = 'NUMBER' AND numeric_value IS NOT NULL)
        OR (data_type = 'BOOLEAN' AND boolean_value IS NOT NULL)
        OR (data_type = 'DATE' AND date_value IS NOT NULL AND date_value BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
    )
);

CREATE UNIQUE INDEX uk_attribute_text_value ON attribute_values (attribute_id, text_value)
    WHERE text_value IS NOT NULL;
CREATE UNIQUE INDEX uk_attribute_numeric_value ON attribute_values (attribute_id, numeric_value)
    WHERE numeric_value IS NOT NULL;
CREATE UNIQUE INDEX uk_attribute_boolean_value ON attribute_values (attribute_id, boolean_value)
    WHERE boolean_value IS NOT NULL;
CREATE UNIQUE INDEX uk_attribute_date_value ON attribute_values (attribute_id, date_value)
    WHERE date_value IS NOT NULL;

CREATE TABLE category_attributes (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    attribute_id UUID NOT NULL REFERENCES attribute_definitions (id) ON DELETE RESTRICT,
    enabled BOOLEAN NOT NULL,
    required BOOLEAN NOT NULL,
    option_allowed BOOLEAN NOT NULL,
    filterable BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_category_attribute UNIQUE (category_id, attribute_id),
    CHECK (enabled OR (NOT required AND NOT option_allowed AND NOT filterable))
);

CREATE INDEX idx_category_attributes_attribute ON category_attributes (attribute_id);
