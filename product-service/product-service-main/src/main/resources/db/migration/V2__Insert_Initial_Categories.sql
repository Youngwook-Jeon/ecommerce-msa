-- Version 2: Insert initial sample data for categories.
INSERT INTO categories (id, name, parent_id, status)
VALUES (1, 'Electronics', NULL, 'ACTIVE'),
       (2, 'Books', NULL, 'ACTIVE'),
       (3, 'Clothing', NULL, 'INACTIVE');

-- Insert Child Categories
INSERT INTO categories (id, name, parent_id, status)
VALUES (4, 'Laptops', 1, 'ACTIVE'),     -- Child of Electronics
       (5, 'Smartphones', 1, 'ACTIVE'), -- Child of Electronics
       (6, 'Fiction', 2, 'ACTIVE'),     -- Child of Books
       (7, 'Non-Fiction', 2, 'ACTIVE'); -- Child of Books

-- Insert Grandchild Category
INSERT INTO categories (id, name, parent_id, status)
VALUES (8, 'Science Fiction', 6, 'ACTIVE');

-- Update the sequence after manual ID insertion.
SELECT setval('categories_id_seq', (SELECT MAX(id) FROM categories));