-- Version 1: Schema for product development

-- ============================================================================
-- ENUM 타입 정의
-- ============================================================================
CREATE TYPE category_status AS ENUM ('ACTIVE', 'INACTIVE', 'DELETED');
CREATE TYPE product_status AS ENUM ('DRAFT', 'ACTIVE', 'INACTIVE', 'DISCONTINUED', 'OUT_OF_STOCK', 'DELETED');
CREATE TYPE condition_type_enum AS ENUM ('NEW', 'USED', 'REFURBISHED', 'OPEN_BOX');
CREATE TYPE option_status AS ENUM ('ACTIVE', 'INACTIVE', 'DELETED');

CREATE CAST (varchar AS category_status) WITH INOUT AS IMPLICIT;
CREATE CAST (smallint AS category_status) WITH INOUT AS IMPLICIT;
CREATE CAST (varchar AS option_status) WITH INOUT AS IMPLICIT;
CREATE CAST (smallint AS option_status) WITH INOUT AS IMPLICIT;

-- ============================================================================
-- 테이블 정의
-- ============================================================================

-- 카테고리 테이블
CREATE TABLE categories
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50)     NOT NULL,
    parent_id  BIGINT,
    status     category_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ              DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ              DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id)
            REFERENCES categories (id)
            ON DELETE SET NULL
            ON UPDATE CASCADE
);

-- 제품 테이블
CREATE TABLE products
(
    id             UUID PRIMARY KEY        DEFAULT uuidv7(),
    category_id    BIGINT         REFERENCES categories (id) ON DELETE SET NULL,
    name           VARCHAR(255)   NOT NULL,
    description    TEXT,
    base_price     DECIMAL(12, 2) NOT NULL CHECK (base_price >= 0),
    status         product_status NOT NULL DEFAULT 'DRAFT',
    condition_type condition_type_enum     DEFAULT 'NEW',
    brand          VARCHAR(100),
    main_image_url VARCHAR(500)   NOT NULL,
    created_at     TIMESTAMPTZ             DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ             DEFAULT CURRENT_TIMESTAMP
);

-- 글로벌 옵션 그룹 (예: "RAM", "Storage", "Color")
CREATE TABLE option_groups
(
    id           UUID PRIMARY KEY       DEFAULT uuidv7(),
    name         VARCHAR(100)  NOT NULL,
    display_name VARCHAR(100)  NOT NULL,
    status       option_status NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_option_groups_name UNIQUE (name)
);

-- 글로벌 옵션 값 (예: "8GB", "16GB", "Silver")
CREATE TABLE option_values
(
    id              UUID PRIMARY KEY       DEFAULT uuidv7(),
    option_group_id UUID          NOT NULL REFERENCES option_groups (id) ON DELETE RESTRICT,
    value           VARCHAR(100)  NOT NULL,
    display_name    VARCHAR(100)  NOT NULL,
    sort_order      INTEGER       NOT NULL DEFAULT 0,
    status          option_status NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_option_values_group_value UNIQUE (option_group_id, value)
);

-- 어떤 상품이 어떤 옵션 그룹을 쓰는지 + 순서/필수 여부
CREATE TABLE product_option_groups
(
    id              UUID PRIMARY KEY       DEFAULT uuidv7(),
    product_id      UUID          NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    option_group_id UUID          NOT NULL REFERENCES option_groups (id) ON DELETE RESTRICT,
    step_order      INTEGER       NOT NULL CHECK (step_order > 0),
    is_required     BOOLEAN       NOT NULL DEFAULT true,
    status          option_status NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_group UNIQUE (product_id, option_group_id),
    CONSTRAINT uk_product_step UNIQUE (product_id, step_order)
);

-- 해당 상품에서 허용되는 옵션 값 + 가격 차이
CREATE TABLE product_option_values
(
    id                      UUID PRIMARY KEY        DEFAULT uuidv7(),
    product_option_group_id UUID           NOT NULL REFERENCES product_option_groups (id) ON DELETE CASCADE,
    option_value_id         UUID           NOT NULL REFERENCES option_values (id) ON DELETE RESTRICT,
    price_delta             DECIMAL(12, 2) NOT NULL DEFAULT 0,
    is_default              BOOLEAN        NOT NULL DEFAULT false,
    status                  option_status  NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pog_value UNIQUE (product_option_group_id, option_value_id)
);

-- 상품의 변형: 재고/주문 단위
CREATE TABLE product_variants
(
    id               UUID PRIMARY KEY        DEFAULT uuidv7(),
    product_id       UUID           NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku              VARCHAR(100)   NOT NULL,
    stock_quantity   INTEGER        NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    status           product_status NOT NULL DEFAULT 'DRAFT',
    calculated_price DECIMAL(12, 2) NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_product_variants_sku UNIQUE (sku)
);

-- 상품 변형의 실제 옵션 조합
CREATE TABLE variant_option_values
(
    id                      UUID PRIMARY KEY     DEFAULT uuidv7(),
    variant_id              UUID        NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    product_option_value_id UUID        NOT NULL REFERENCES product_option_values (id) ON DELETE RESTRICT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_variant_pov UNIQUE (variant_id, product_option_value_id)
);

-- ============================================================================
-- 인덱스
-- ============================================================================

-- 카테고리 인덱스
CREATE INDEX idx_category_name ON categories (name);
CREATE INDEX idx_category_parent_id ON categories (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_category_status ON categories (status) WHERE status = 'ACTIVE';

-- 제품 인덱스
CREATE INDEX idx_products_category_status ON products (category_id, status);
CREATE INDEX idx_products_created_at_id ON products (created_at DESC, id DESC);
CREATE INDEX idx_products_brand ON products (brand) WHERE brand IS NOT NULL;
CREATE INDEX idx_products_status ON products (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_products_price ON products (base_price) WHERE status = 'ACTIVE';
CREATE INDEX idx_products_name ON products (name);

-- 옵션 사전 인덱스 (조회 최적화)
CREATE INDEX idx_option_groups_status ON option_groups (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_option_values_status ON option_values (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_option_values_group_id ON option_values (option_group_id);

-- 연관 테이블 인덱스 (JOIN 최적화)
CREATE INDEX idx_pog_product_id ON product_option_groups (product_id);
CREATE INDEX idx_pog_option_group_id ON product_option_groups (option_group_id);
CREATE INDEX idx_pov_pog_id ON product_option_values (product_option_group_id);
CREATE INDEX idx_pov_option_value_id ON product_option_values (option_value_id);
CREATE INDEX idx_variants_product_id ON product_variants (product_id);
CREATE INDEX idx_vov_variant_id ON variant_option_values (variant_id);
CREATE INDEX idx_vov_pov_id ON variant_option_values (product_option_value_id);