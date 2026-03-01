-- Version 1: Schema for product development

-- ============================================================================
-- ENUM 타입 정의
-- ============================================================================
CREATE TYPE category_status AS ENUM ('ACTIVE', 'INACTIVE', 'DELETED');
CREATE TYPE product_status AS ENUM ('ACTIVE', 'INACTIVE', 'DISCONTINUED', 'OUT_OF_STOCK', 'DELETED');
CREATE TYPE condition_type_enum AS ENUM ('NEW', 'USED', 'REFURBISHED', 'OPEN_BOX');

CREATE CAST (varchar AS category_status) WITH INOUT AS IMPLICIT;
CREATE CAST (smallint AS category_status) WITH INOUT AS IMPLICIT;

-- ============================================================================
-- 테이블 정의
-- ============================================================================

-- 카테고리 테이블
CREATE TABLE categories
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(50)    NOT NULL,
    parent_id  BIGINT,
    status     category_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP               DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id)
            REFERENCES categories (id)
            ON DELETE SET NULL
            ON UPDATE CASCADE
);

-- 제품 테이블
CREATE TABLE products
(
    id             UUID PRIMARY KEY             DEFAULT uuidv7(),
    category_id    BIGINT REFERENCES categories (id) ON DELETE SET NULL,
    name           VARCHAR(255)        NOT NULL,
    description    TEXT,
    base_price     DECIMAL(12, 2)      NOT NULL CHECK (base_price >= 0),
    status         product_status      NOT NULL DEFAULT 'ACTIVE',
    condition_type condition_type_enum          DEFAULT 'NEW',
    brand          VARCHAR(100),
    created_at     TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                    DEFAULT CURRENT_TIMESTAMP
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
CREATE INDEX idx_products_brand ON products (brand) WHERE brand IS NOT NULL;
CREATE INDEX idx_products_status ON products (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_products_price ON products (base_price) WHERE status = 'ACTIVE';
CREATE INDEX idx_products_name ON products (name);
