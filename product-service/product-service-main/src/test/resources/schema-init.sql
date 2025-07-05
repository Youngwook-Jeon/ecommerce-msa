DROP SCHEMA IF EXISTS product CASCADE;

CREATE SCHEMA product;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS product.product
(
    product_id UUID PRIMARY KEY,
    product_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL,
    price NUMERIC(10, 2) NOT NULL
    );

CREATE TABLE IF NOT EXISTS product.categories
(
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    parent_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_category_parent
    FOREIGN KEY (parent_id)
    REFERENCES product.categories(id)
    ON DELETE SET NULL
    ON UPDATE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_category_name ON product.categories (name);

CREATE INDEX IF NOT EXISTS idx_category_parent_id ON product.categories (parent_id);