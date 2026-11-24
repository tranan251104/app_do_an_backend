ALTER TABLE promotions ADD COLUMN demo_benefit VARCHAR(16)
    CHECK (demo_benefit IN ('percentage', 'fixedDiscount', 'cashback'));

WITH partners(position, title, category) AS (
    VALUES
        (1, 'Đường sắt Việt Nam', 'Di chuyển'),
        (2, 'Traveloka', 'Du lịch'),
        (3, 'LOTTE Mart', 'Mua sắm'),
        (4, 'CGV', 'Giải trí'),
        (5, 'KFC', 'Ăn uống'),
        (6, 'Highlands Coffee', 'Ăn uống'),
        (7, 'Grab', 'Di chuyển'),
        (8, 'Shopee', 'Mua sắm'),
        (9, 'Lazada', 'Mua sắm'),
        (10, 'Tiki', 'Công nghệ'),
        (11, 'WinMart', 'Mua sắm'),
        (12, 'Garena', 'Giải trí'),
        (13, 'Agoda', 'Du lịch'),
        (14, 'Apple Store', 'Công nghệ'),
        (15, 'FPT Shop', 'Công nghệ'),
        (16, 'Canifa', 'Mua sắm'),
        (17, 'H&M', 'Mua sắm'),
        (18, 'Guardian', 'Sức khỏe'),
        (19, 'Pharmacity', 'Sức khỏe'),
        (20, 'Decathlon', 'Mua sắm'),
        (21, 'The Coffee House', 'Ăn uống'),
        (22, 'Green SM Food', 'Ăn uống'),
        (23, 'ShopeeFood', 'Ăn uống'),
        (24, 'Pizza 4P''s', 'Ăn uống'),
        (25, 'Starbucks', 'Ăn uống'),
        (26, 'Burger King', 'Ăn uống'),
        (27, 'Sapporo Beer', 'Ăn uống'),
        (28, 'Xe khách Phương Trang', 'Di chuyển'),
        (29, 'Metro Hà Nội', 'Di chuyển'),
        (30, 'BeCar', 'Di chuyển'),
        (31, 'Netflix', 'Giải trí'),
        (32, 'Thế Giới Di Động', 'Công nghệ'),
        (33, 'Điện Máy Xanh', 'Công nghệ'),
        (34, 'CellphoneS', 'Công nghệ'),
        (35, 'Uniqlo', 'Mua sắm'),
        (36, 'Zara', 'Mua sắm'),
        (37, 'Vinpearl', 'Du lịch'),
        (38, 'Furama Resort Đà Nẵng', 'Du lịch'),
        (39, 'Vietnam Airlines', 'Du lịch'),
        (40, 'Bệnh viện Vinmec', 'Sức khỏe'),
        (41, 'MEDIC Hòa Hảo', 'Sức khỏe'),
        (42, 'California Fitness', 'Sức khỏe'),
        (43, 'Coursera', 'Giáo dục'),
        (44, 'IELTS Fighter', 'Giáo dục'),
        (45, 'CG3D', 'Giáo dục'),
        (46, 'Con Cưng', 'Mua sắm'),
        (47, 'PetMart', 'Mua sắm')
), benefits(group_number, kind, description, expiry_days) AS (
    VALUES
        (0, 'percentage', 'Ưu đãi mô phỏng · Giảm 10%', 7),
        (1, 'fixedDiscount', 'Ưu đãi mô phỏng · Giảm 20.000 đ', 180),
        (2, 'cashback', 'Ưu đãi mô phỏng · Hoàn 10.000 đ', 365)
)
INSERT INTO promotions (id, code, title, description, category, valid_from, valid_until, demo_benefit)
SELECT gen_random_uuid(), 'ANPAY-DEMO-' || lpad((n + 1)::text, 3, '0'),
       partners.title, benefits.description, partners.category,
       CURRENT_TIMESTAMP - (n + 1) * INTERVAL '1 day',
       CURRENT_TIMESTAMP + (benefits.expiry_days + n % 12) * INTERVAL '1 day',
       benefits.kind
FROM generate_series(0, 99) AS offer(n)
JOIN partners ON partners.position = n % 47 + 1
JOIN benefits ON benefits.group_number = n / 47
ON CONFLICT (code) DO NOTHING;
