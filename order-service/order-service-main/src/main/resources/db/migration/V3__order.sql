-- V3: Order snapshot tables (schema: orders)

CREATE TABLE orders
(
    id                      UUID PRIMARY KEY        DEFAULT uuidv7(),
    user_id                 VARCHAR(36)             NOT NULL,
    status                  VARCHAR(32)             NOT NULL,
    subtotal_amount         DECIMAL(12, 2)          NOT NULL,
    shipping_amount         DECIMAL(12, 2)          NOT NULL DEFAULT 0,
    total_amount            DECIMAL(12, 2)          NOT NULL,
    shipping_recipient_name VARCHAR(100)            NOT NULL,
    shipping_phone          VARCHAR(30)             NOT NULL,
    shipping_address_line1  VARCHAR(255)            NOT NULL,
    shipping_address_line2  VARCHAR(255),
    shipping_city           VARCHAR(100)            NOT NULL,
    shipping_postal_code    VARCHAR(20)             NOT NULL,
    shipping_country_code   VARCHAR(2)              NOT NULL,
    created_at              TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ             NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_orders_subtotal_non_negative CHECK (subtotal_amount >= 0),
    CONSTRAINT ck_orders_shipping_non_negative CHECK (shipping_amount >= 0),
    CONSTRAINT ck_orders_total_non_negative CHECK (total_amount >= 0),
    CONSTRAINT ck_orders_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
);

COMMENT ON TABLE orders IS 'Placed order aggregate root with shipping snapshot';
COMMENT ON COLUMN orders.status IS 'PENDING_PAYMENT reserved for payment saga; Phase 1 uses CONFIRMED';

CREATE TABLE order_lines
(
    id                     UUID PRIMARY KEY        DEFAULT uuidv7(),
    order_id               UUID                    NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
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
    CONSTRAINT ck_order_lines_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_lines_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_order_lines_variant_options_array CHECK (jsonb_typeof(variant_options_json) = 'array')
);

COMMENT ON TABLE order_lines IS 'Immutable catalog snapshot per order line at checkout time';

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_order_lines_order_id ON order_lines (order_id);
CREATE INDEX idx_order_lines_product_id ON order_lines (product_id);
CREATE INDEX idx_order_lines_product_variant_id ON order_lines (product_variant_id);
