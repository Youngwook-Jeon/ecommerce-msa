-- Version 1: Initial schema setup for product and categories tables.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE product
(
    product_id   UUID PRIMARY KEY,
    product_name VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(200) NOT NULL,
    price        NUMERIC(10, 2) NOT NULL
);

CREATE TABLE categories
(
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(50)  NOT NULL,
    parent_id BIGINT,
    status    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id)
            REFERENCES categories (id)
            ON DELETE SET NULL
            ON UPDATE CASCADE
);

CREATE INDEX idx_category_name ON categories (name);

CREATE INDEX idx_category_parent_id ON categories (parent_id);