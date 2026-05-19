-- Public PLP keyword search: single pg_trgm GIN on lower(name) || lower(brand).
-- Queries must use the same expression as the index for planner use.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_products_name_brand_search_trgm
    ON products USING gin (
        (lower(coalesce(name, '')) || ' ' || lower(coalesce(brand, ''))) gin_trgm_ops
    );
