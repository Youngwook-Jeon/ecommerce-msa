-- Version 1: Initial schema setup for products and categories tables.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 0. 카테고리 테이블
CREATE TABLE categories
(
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(50) NOT NULL,
    parent_id BIGINT,
    status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id)
            REFERENCES categories (id)
            ON DELETE SET NULL
            ON UPDATE CASCADE
);

-- 1. 기본 제품 테이블
CREATE TABLE products
(
    id             UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    category_id    BIGINT NULL REFERENCES categories (id) ON DELETE SET NULL,
    name           VARCHAR(255)   NOT NULL,
    description    TEXT,
    base_price     DECIMAL(12, 2) NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    condition_type VARCHAR(50)             DEFAULT 'NEW',
    brand_id       UUID           REFERENCES brands (id) ON DELETE SET NULL,
    created_at     TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP               DEFAULT CURRENT_TIMESTAMP
);

-- 2. 옵션 그룹 테이블 (색상, 저장용량, 메모리 등)
CREATE TABLE option_groups
(
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name         VARCHAR(100) NOT NULL,             -- '색상', '저장용량', '메모리'
    code         VARCHAR(50)  NOT NULL UNIQUE,      -- 'color', 'storage', 'memory'
    display_type VARCHAR(20)      DEFAULT 'select', -- 'select', 'radio', 'swatch'
    created_at   TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

-- 3. 옵션 값 테이블 (스페이스 그레이, 1TB, 16GB 등)
CREATE TABLE option_values
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    option_group_id UUID         NOT NULL REFERENCES option_groups (id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL, -- '스페이스 그레이', '1TB', '16GB'
    code            VARCHAR(50)  NOT NULL, -- 'space_gray', '1tb', '16gb'
    hex_color       VARCHAR(7),            -- 색상인 경우 hex 값
    sort_order      INTEGER          DEFAULT 0,
    is_active       BOOLEAN          DEFAULT true,
    created_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (option_group_id, code)
);

-- 4. 제품별 사용 가능한 옵션 그룹 (순서 포함)
CREATE TABLE product_option_groups
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id      UUID    NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    option_group_id UUID    NOT NULL REFERENCES option_groups (id) ON DELETE CASCADE,
    step_order      INTEGER NOT NULL, -- 선택 순서 (1, 2, 3, ...)
    is_required     BOOLEAN          DEFAULT true,
    created_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, option_group_id),
    UNIQUE (product_id, step_order)
);

-- 5. 제품별 사용 가능한 옵션 값 및 가격 정보
CREATE TABLE product_option_values
(
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_option_group_id UUID NOT NULL REFERENCES product_option_groups (id) ON DELETE CASCADE,
    option_value_id         UUID NOT NULL REFERENCES option_values (id) ON DELETE CASCADE,
    price_adjustment        DECIMAL(12, 2)   DEFAULT 0, -- 추가 가격
    is_default              BOOLEAN          DEFAULT false,
    is_active               BOOLEAN          DEFAULT true,
    created_at              TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_option_group_id, option_value_id)
);

-- 6. 제품 변형 (옵션 조합으로 만들어진 실제 상품)
CREATE TABLE product_variants
(
    id             UUID PRIMARY KEY        DEFAULT uuid_generate_v4(),
    product_id     UUID           NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    sku            VARCHAR(100)   NOT NULL UNIQUE,
    final_price    DECIMAL(12, 2) NOT NULL,
    stock_quantity INTEGER                 DEFAULT 0,
    status         VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP               DEFAULT CURRENT_TIMESTAMP
);

-- 7. 제품 변형의 옵션 조합
CREATE TABLE variant_option_values
(
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    variant_id              UUID NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    product_option_value_id UUID NOT NULL REFERENCES product_option_values (id) ON DELETE CASCADE,
    created_at              TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (variant_id, product_option_value_id)
);

-- 8. 이미지 테이블
CREATE TABLE images
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    url        VARCHAR(500) NOT NULL,
    alt_text   VARCHAR(255),
    sort_order INTEGER          DEFAULT 0,
    created_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

-- 9. 제품 기본 이미지
CREATE TABLE product_images
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    image_id   UUID NOT NULL REFERENCES images (id) ON DELETE CASCADE,
    is_primary BOOLEAN          DEFAULT false,
    sort_order INTEGER          DEFAULT 0,
    created_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, image_id)
);

-- 10. 옵션 값별 이미지 (예: 색상 선택 시 변경되는 이미지)
CREATE TABLE option_value_images
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    option_value_id UUID NOT NULL REFERENCES option_values (id) ON DELETE CASCADE,
    image_id        UUID NOT NULL REFERENCES images (id) ON DELETE CASCADE,
    sort_order      INTEGER          DEFAULT 0,
    created_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (option_value_id, image_id)
);

-- 11. 제품 변형별 이미지 (특정 조합에만 해당하는 이미지)
CREATE TABLE variant_images
(
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    variant_id UUID NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    image_id   UUID NOT NULL REFERENCES images (id) ON DELETE CASCADE,
    is_primary BOOLEAN          DEFAULT false,
    sort_order INTEGER          DEFAULT 0,
    created_at TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (variant_id, image_id)
);

-- 12. 옵션 값 간 제약 조건 (특정 조합 불가능)
CREATE TABLE option_constraints
(
    id                     UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id             UUID        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    constraint_type        VARCHAR(20) NOT NULL, -- 'excludes', 'requires'
    source_option_value_id UUID        NOT NULL REFERENCES option_values (id) ON DELETE CASCADE,
    target_option_value_id UUID        NOT NULL REFERENCES option_values (id) ON DELETE CASCADE,
    created_at             TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    CHECK (constraint_type IN ('excludes', 'requires'))
);

-- 13. 브랜드 테이블
CREATE TABLE brands
(
    id         UUID PRIMARY KEY      DEFAULT uuid_generate_v4(),
    name       VARCHAR(100) NOT NULL UNIQUE,
    logo_url   VARCHAR(500),
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

-- 14. 필터 속성 테이블 (브랜드, 상품상태, 메모리용량 등)
CREATE TABLE filter_attributes
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    option_group_id UUID NULL REFERENCES option_groups (id) ON DELETE SET NULL,
    name            VARCHAR(100) NOT NULL,        -- '상품 상태', '메모리 용량'
    code            VARCHAR(50)  NOT NULL UNIQUE, -- 'condition', 'memory_capacity'
    display_type    VARCHAR(20)  NOT NULL,        -- 'select', 'range', 'color_swatch' 등 UI 표시 형태
    created_at      TIMESTAMP        DEFAULT CURRENT_TIMESTAMP
);

-- 15. 필터 값 테이블
CREATE TABLE filter_values
(
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    filter_attribute_id UUID NOT NULL REFERENCES filter_attributes (id) ON DELETE CASCADE,
    option_value_id     UUID NULL REFERENCES option_values (id) ON DELETE SET NULL,
    -- 필터 값 자체를 저장하는 컬럼 (옵션과 연동되지 않는 필터용)
    value               VARCHAR(100), -- 'Apple', 'Samsung', '새상품'
    display_name        VARCHAR(100), -- 'Apple', '삼성', '새상품' (화면 표시용)
    sort_order          INTEGER          DEFAULT 0,
    created_at          TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (filter_attribute_id, value),
    UNIQUE (filter_attribute_id, option_value_id)
);

-- 16. 카테고리별 사용 가능한 필터 속성
CREATE TABLE category_filter_attributes
(
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    category_id         BIGINT NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    filter_attribute_id UUID   NOT NULL REFERENCES filter_attributes (id) ON DELETE CASCADE,
    display_order       INTEGER          DEFAULT 0,     -- 필터 표시 순서
    is_required         BOOLEAN          DEFAULT false,
    is_featured         BOOLEAN          DEFAULT false, -- 주요 필터 여부
    created_at          TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (category_id, filter_attribute_id)
);

-- 17. 제품별 필터 값 매핑
CREATE TABLE product_filter_values
(
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id          UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    filter_attribute_id UUID NOT NULL REFERENCES filter_attributes (id) ON DELETE CASCADE,
    filter_value_id     UUID REFERENCES filter_values (id) ON DELETE CASCADE, -- 선택형 필터용
    custom_value        VARCHAR(255),                                         -- 커스텀 값 (범위형 등)
    numeric_value       DECIMAL(15, 4),                                       -- 숫자 범위 필터링용
    created_at          TIMESTAMP        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (product_id, filter_attribute_id, filter_value_id)
);

-- 성능 최적화를 위한 인덱스
CREATE INDEX idx_category_name ON categories (name);
CREATE INDEX idx_category_parent_id ON categories (parent_id);
CREATE INDEX idx_products_category ON products (category_id, status);
CREATE INDEX idx_product_option_groups_product_step ON product_option_groups (product_id, step_order);
CREATE INDEX idx_product_option_values_group ON product_option_values (product_option_group_id, is_active);
CREATE INDEX idx_variant_option_values_variant ON variant_option_values (variant_id);
CREATE INDEX idx_option_values_group_sort ON option_values (option_group_id, sort_order);
CREATE INDEX idx_product_variants_product ON product_variants (product_id, status);
CREATE INDEX idx_option_constraints_product ON option_constraints (product_id, source_option_value_id);
CREATE INDEX idx_product_filter_values_product ON product_filter_values (product_id);
CREATE INDEX idx_product_filter_values_filter ON product_filter_values (filter_attribute_id, filter_value_id);
CREATE INDEX idx_product_filter_values_numeric ON product_filter_values (filter_attribute_id, numeric_value);
CREATE INDEX idx_category_filter_attributes_category ON category_filter_attributes (category_id, display_order);
CREATE INDEX idx_filter_values_attribute ON filter_values (filter_attribute_id, sort_order);
