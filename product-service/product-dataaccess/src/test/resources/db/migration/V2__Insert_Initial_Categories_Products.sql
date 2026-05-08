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
       (7, 'Non-Fiction', 2, 'ACTIVE');
-- Child of Books

-- Insert Grandchild Category
INSERT INTO categories (id, name, parent_id, status)
VALUES (8, 'Science Fiction', 6, 'ACTIVE');

-- Update the sequence after manual ID insertion.
SELECT setval('categories_id_seq', (SELECT MAX(id) FROM categories));

DO
$$
DECLARE
    -- 글로벌 식별자 변수
v_color_group UUID;
    v_storage_group
UUID;
    v_val_black
UUID;
    v_val_white
UUID;
    v_val_256
UUID;
    v_val_512
UUID;

    -- 루프 내 로컬 식별자 변수
    v_product_id
UUID;
    v_pog_color
UUID;
    v_pog_storage
UUID;
    v_pov_black
UUID;
    v_pov_white
UUID;
    v_pov_256
UUID;
    v_pov_512
UUID;
    v_variant_1
UUID;
    v_variant_2
UUID;

    -- 달러 기준 가격 변수 (DECIMAL 12,2)
    v_base_price
DECIMAL(12, 2);
    v_delta_512
DECIMAL(12, 2) := 100.00; -- 512GB 업그레이드 시 $100.00 추가
BEGIN
    -- ========================================================================
    -- 1. 글로벌 옵션 사전(Dictionary) 세팅
    -- ========================================================================
INSERT INTO option_groups (name, display_name)
VALUES ('Color', 'Color') RETURNING id
INTO v_color_group;
INSERT INTO option_groups (name, display_name)
VALUES ('Storage', 'Storage') RETURNING id
INTO v_storage_group;

INSERT INTO option_values (option_group_id, value, display_name, sort_order)
VALUES (v_color_group, 'BLACK', 'Midnight Black', 1) RETURNING id
INTO v_val_black;
INSERT INTO option_values (option_group_id, value, display_name, sort_order)
VALUES (v_color_group, 'WHITE', 'Starlight White', 2) RETURNING id
INTO v_val_white;
INSERT INTO option_values (option_group_id, value, display_name, sort_order)
VALUES (v_storage_group, '256GB', '256GB', 1) RETURNING id
INTO v_val_256;
INSERT INTO option_values (option_group_id, value, display_name, sort_order)
VALUES (v_storage_group, '512GB', '512GB', 2) RETURNING id
INTO v_val_512;

-- ========================================================================
-- 2. 205개 DRAFT 상품 및 연관 하위 데이터 일괄 생성
-- ========================================================================
FOR i IN 1..205 LOOP

        -- [가격 계산] $199.99 부터 시작해서 상품마다 $10.00 씩 증가
        v_base_price := 199.99 + (i * 10.00);

        -- [상품 생성] DRAFT 상태로 생성
INSERT INTO products (category_id, name, description, base_price, status, condition_type, brand, main_image_url)
VALUES ((i % 5) + 4, -- 기존에 등록된 4~8번 하위 카테고리 순환 배치
        'Virtual DRAFT Model ' || i,
        'Automatically generated test DRAFT product description. Model Number: ' || i,
        v_base_price,
        'DRAFT',
        'NEW',
        CASE WHEN i % 3 = 0 THEN 'Apple' WHEN i % 3 = 1 THEN 'Samsung' ELSE 'Sony' END,
        'https://dummyimage.com/600x400/000/fff&text=Product+' || i) RETURNING id
INTO v_product_id;

-- [상품 옵션 그룹] 색상, 용량 필수 옵션으로 추가
INSERT INTO product_option_groups (product_id, option_group_id, step_order, is_required)
VALUES (v_product_id, v_color_group, 1, true) RETURNING id
INTO v_pog_color;

INSERT INTO product_option_groups (product_id, option_group_id, step_order, is_required)
VALUES (v_product_id, v_storage_group, 2, true) RETURNING id
INTO v_pog_storage;

-- [상품 옵션 밸류]
INSERT INTO product_option_values (product_option_group_id, option_value_id, price_delta)
VALUES (v_pog_color, v_val_black, 0.00) RETURNING id
INTO v_pov_black;

INSERT INTO product_option_values (product_option_group_id, option_value_id, price_delta)
VALUES (v_pog_color, v_val_white, 0.00) RETURNING id
INTO v_pov_white;

INSERT INTO product_option_values (product_option_group_id, option_value_id, price_delta)
VALUES (v_pog_storage, v_val_256, 0.00) RETURNING id
INTO v_pov_256;

INSERT INTO product_option_values (product_option_group_id, option_value_id, price_delta)
VALUES (v_pog_storage, v_val_512, v_delta_512) RETURNING id
INTO v_pov_512;
-- 512GB는 $100.00 추가

-- [상품 변형 생성 1] 블랙 + 256GB (DRAFT) - 기본 가격
INSERT INTO product_variants (product_id, sku, stock_quantity, status, calculated_price)
VALUES (v_product_id, 'SKU-DRAFT-' || i || '-BLK-256', 100, 'DRAFT', v_base_price) RETURNING id
INTO v_variant_1;

INSERT INTO variant_option_values (variant_id, product_option_value_id)
VALUES (v_variant_1, v_pov_black);
INSERT INTO variant_option_values (variant_id, product_option_value_id)
VALUES (v_variant_1, v_pov_256);

-- [상품 변형 생성 2] 화이트 + 512GB (DRAFT) - 기본 가격 + $100.00
INSERT INTO product_variants (product_id, sku, stock_quantity, status, calculated_price)
VALUES (v_product_id, 'SKU-DRAFT-' || i || '-WHT-512', 50, 'DRAFT', v_base_price + v_delta_512) RETURNING id
INTO v_variant_2;

INSERT INTO variant_option_values (variant_id, product_option_value_id)
VALUES (v_variant_2, v_pov_white);
INSERT INTO variant_option_values (variant_id, product_option_value_id)
VALUES (v_variant_2, v_pov_512);

END LOOP;
END $$;