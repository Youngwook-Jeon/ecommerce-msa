-- Product images (R2 object metadata + main/gallery roles)

CREATE TABLE product_images
(
    id           UUID PRIMARY KEY       DEFAULT uuidv7(),
    product_id   UUID          NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    storage_key  VARCHAR(512)  NOT NULL,
    public_url   VARCHAR(1000) NOT NULL,
    role         VARCHAR(20)   NOT NULL CHECK (role IN ('MAIN', 'GALLERY')),
    sort_order   INTEGER       NOT NULL DEFAULT 0,
    content_type VARCHAR(100),
    file_size    BIGINT,
    status       option_status NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_images_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_product_images_product_status ON product_images (product_id, status);

CREATE UNIQUE INDEX uk_product_one_active_main
    ON product_images (product_id) WHERE status = 'ACTIVE' AND role = 'MAIN';
