-- A fixed specification is unique per product and definition. Purchase-option exclusivity
-- is enforced by the product write transaction using the shared catalog revision.
CREATE TABLE product_attributes (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    attribute_id UUID NOT NULL REFERENCES attribute_definitions(id) ON DELETE RESTRICT,
    attribute_value_id UUID NOT NULL,
    CONSTRAINT uk_product_attribute UNIQUE (product_id, attribute_id),
    CONSTRAINT fk_product_attribute_value FOREIGN KEY (attribute_id, attribute_value_id)
        REFERENCES attribute_values(attribute_id, id) ON DELETE RESTRICT
);
CREATE INDEX idx_product_attributes_definition ON product_attributes(attribute_id);
CREATE INDEX idx_product_attributes_value ON product_attributes(attribute_value_id);
