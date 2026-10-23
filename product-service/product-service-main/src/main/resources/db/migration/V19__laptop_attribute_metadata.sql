-- Initial metadata only: existing product specifications and purchase options are not modified.
INSERT INTO attribute_definitions (code, display_name, data_type, canonical_unit)
VALUES ('ram_capacity', 'Memory', 'NUMBER', 'GB'),
       ('storage_capacity', 'Storage', 'NUMBER', 'GB'),
       ('color', 'Color', 'ENUM', NULL),
       ('weight', 'Weight', 'NUMBER', 'g'),
       ('release_date', 'Release date', 'DATE', NULL);

INSERT INTO attribute_values (attribute_id, data_type, display_name, numeric_value)
SELECT a.id, a.data_type, v.label, v.amount
FROM attribute_definitions a
JOIN (VALUES ('ram_capacity', '16 GB', 16), ('ram_capacity', '32 GB', 32),
             ('storage_capacity', '512 GB', 512), ('storage_capacity', '1 TB', 1000),
             ('weight', '1,240 g', 1240)) AS v(code, label, amount) ON a.code = v.code;

INSERT INTO attribute_values (attribute_id, data_type, display_name, text_value)
SELECT a.id, a.data_type, v.label, v.value
FROM attribute_definitions a
JOIN (VALUES ('Black', 'BLACK'), ('Silver', 'SILVER')) AS v(label, value) ON a.code = 'color';

INSERT INTO attribute_values (attribute_id, data_type, display_name, date_value)
SELECT id, data_type, '2026-03-01', DATE '2026-03-01'
FROM attribute_definitions WHERE code = 'release_date';

-- Apply only to the existing sample Laptops branch, without changing its ancestry.
INSERT INTO category_attributes
    (category_id, attribute_id, enabled, required, option_allowed, filterable, display_order)
SELECT c.id, a.id, true, a.code IN ('ram_capacity', 'storage_capacity', 'weight'),
       a.code IN ('ram_capacity', 'storage_capacity', 'color'), true,
       CASE a.code WHEN 'ram_capacity' THEN 10 WHEN 'storage_capacity' THEN 20
                   WHEN 'color' THEN 30 WHEN 'weight' THEN 40 ELSE 50 END
FROM categories c CROSS JOIN attribute_definitions a
WHERE c.name = 'Laptops' AND c.status <> 'DELETED'
  AND a.code IN ('ram_capacity', 'storage_capacity', 'color', 'weight', 'release_date');
