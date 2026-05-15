-- Option value images + variant denormalized main image + visual option group flag

ALTER TABLE product_option_groups
    ADD COLUMN drives_variant_images BOOLEAN NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uk_pog_one_visual_per_product
    ON product_option_groups (product_id)
    WHERE drives_variant_images = true AND status = 'ACTIVE';

ALTER TABLE product_variants
    ADD COLUMN main_image_url VARCHAR(1000);

CREATE TABLE product_option_value_images
(
    id                      UUID PRIMARY KEY       DEFAULT uuidv7(),
    product_option_value_id UUID          NOT NULL REFERENCES product_option_values (id) ON DELETE CASCADE,
    storage_key             VARCHAR(512)  NOT NULL,
    public_url              VARCHAR(1000) NOT NULL,
    role                    VARCHAR(20)   NOT NULL CHECK (role IN ('MAIN', 'GALLERY')),
    sort_order              INTEGER       NOT NULL DEFAULT 0,
    content_type            VARCHAR(100),
    file_size               BIGINT,
    status                  option_status NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pov_images_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_pov_images_pov_status
    ON product_option_value_images (product_option_value_id, status);

CREATE UNIQUE INDEX uk_pov_one_active_main
    ON product_option_value_images (product_option_value_id)
    WHERE status = 'ACTIVE' AND role = 'MAIN';
