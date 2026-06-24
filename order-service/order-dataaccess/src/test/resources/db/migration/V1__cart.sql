-- V1: Authenticated-user cart tables (schema: order, see application.yml)
-- Guest carts live in Redis (same item snapshot shape as cart_items).

-- ============================================================================
-- carts — one row per authenticated user (Keycloak JWT sub)
-- ============================================================================
CREATE TABLE carts
(
    id         UUID PRIMARY KEY     DEFAULT uuidv7(),
    user_id    VARCHAR(36)          NOT NULL,
    created_at TIMESTAMPTZ          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_carts_user_id UNIQUE (user_id)
);

COMMENT ON TABLE carts IS 'Logged-in user shopping cart (one per user_id / Keycloak sub)';
COMMENT ON COLUMN carts.user_id IS 'Keycloak JWT claim sub';

-- ============================================================================
-- cart_items — line items with catalog snapshot fields
-- ============================================================================
CREATE TABLE cart_items
(
    id                     UUID PRIMARY KEY        DEFAULT uuidv7(),
    cart_id                UUID                    NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    product_id             UUID                    NOT NULL,
    product_variant_id     UUID                    NOT NULL,
    product_name           VARCHAR(255)            NOT NULL,
    brand                  VARCHAR(100),
    sku                    VARCHAR(100)            NOT NULL,
    image_url              VARCHAR(2048),
    unit_price             DECIMAL(12, 2)          NOT NULL,
    variant_options_json   JSONB                   NOT NULL DEFAULT '[]'::jsonb,
    quantity               INTEGER                 NOT NULL,
    created_at             TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_items_cart_variant UNIQUE (cart_id, product_variant_id),
    CONSTRAINT ck_cart_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_cart_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_cart_items_variant_options_array CHECK (jsonb_typeof(variant_options_json) = 'array')
);

COMMENT ON TABLE cart_items IS 'Cart line items; snapshot fields refreshed on add/update via product-service';
COMMENT ON COLUMN cart_items.unit_price IS 'Price snapshot from variant calculatedPrice at last mutation';
COMMENT ON COLUMN cart_items.variant_options_json IS
    'Selected option snapshot for cart UI, e.g. [{"stepOrder":1,"optionGroupName":"Color","optionValueName":"Black",...}]. Built from PDP optionGroups + variant selectedProductOptionValueIds; empty array when no options.';

-- ============================================================================
-- indexes
-- ============================================================================
CREATE INDEX idx_cart_items_cart_id ON cart_items (cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items (product_id);
CREATE INDEX idx_cart_items_product_variant_id ON cart_items (product_variant_id);
