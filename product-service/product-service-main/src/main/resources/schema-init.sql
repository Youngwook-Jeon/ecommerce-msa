DROP SCHEMA IF EXISTS product CASCADE;

CREATE SCHEMA product;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE product.product
(
    product_id UUID PRIMARY KEY,
    product_name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL,
    price NUMERIC(10, 2) NOT NULL
)