-- =============================================================
-- V3 — Module Danh mục thu chi & Biểu tượng (Category & Icon)
-- =============================================================

-- -------------------------------------------------------------
-- 1. icons — Kho biểu tượng hệ thống
-- -------------------------------------------------------------
CREATE TABLE icons (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    path_data       TEXT         NOT NULL,
    icon_group      VARCHAR(30)  NOT NULL,
    search_keywords TEXT,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_icons_code  UNIQUE (code),
    CONSTRAINT ck_icons_group CHECK (icon_group IN (
        'an_uong', 'di_lai', 'mua_sam', 'giai_tri',
        'suc_khoe', 'hoc_tap', 'tai_chinh', 'khac'
    ))
);

COMMENT ON COLUMN icons.path_data IS 'Đường vẽ SVG biểu tượng';

CREATE INDEX idx_icons_group ON icons (icon_group, sort_order) WHERE is_active;

-- -------------------------------------------------------------
-- 2. category_groups — Nhóm lớn (phục vụ biểu đồ báo cáo)
-- -------------------------------------------------------------
CREATE TABLE category_groups (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(50) NOT NULL,
    icon_id    UUID,
    color      CHAR(7)     NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0,

    CONSTRAINT fk_cg_icon  FOREIGN KEY (icon_id) REFERENCES icons (id) ON DELETE SET NULL,
    CONSTRAINT uq_cg_name  UNIQUE (name),
    CONSTRAINT ck_cg_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);

-- -------------------------------------------------------------
-- 3. categories — Cây danh mục thu chi (tối đa 2 cấp)
-- -------------------------------------------------------------
CREATE TABLE categories (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID,
    parent_category_id UUID,
    category_group_id  UUID,
    name               VARCHAR(50) NOT NULL,
    type               VARCHAR(10) NOT NULL,
    icon_id            UUID        NOT NULL,
    color              CHAR(7)     NOT NULL,
    sort_order         INTEGER     NOT NULL DEFAULT 0,
    is_deleted         BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_cat_user   FOREIGN KEY (user_id)            REFERENCES users           (id) ON DELETE CASCADE,
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_category_id) REFERENCES categories      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cat_group  FOREIGN KEY (category_group_id)  REFERENCES category_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cat_icon   FOREIGN KEY (icon_id)            REFERENCES icons           (id) ON DELETE RESTRICT,

    CONSTRAINT ck_cat_type   CHECK (type IN ('expense', 'income')),
    CONSTRAINT ck_cat_color  CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_cat_root_needs_group CHECK (
        parent_category_id IS NOT NULL OR category_group_id IS NOT NULL
    )
);

COMMENT ON COLUMN categories.user_id            IS 'NULL = danh mục mặc định của hệ thống, dùng chung cho mọi người';
COMMENT ON COLUMN categories.parent_category_id IS 'NULL = danh mục cấp cha. Có giá trị = danh mục con';

CREATE UNIQUE INDEX uq_cat_name_root
    ON categories (COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid), type, lower(name))
    WHERE parent_category_id IS NULL AND NOT is_deleted;
CREATE UNIQUE INDEX uq_cat_name_child
    ON categories (parent_category_id, lower(name))
    WHERE parent_category_id IS NOT NULL AND NOT is_deleted;

CREATE INDEX idx_cat_user   ON categories (user_id, type) WHERE NOT is_deleted;
CREATE INDEX idx_cat_parent ON categories (parent_category_id) WHERE NOT is_deleted;

-- -------------------------------------------------------------
-- Triggers bảo đảm cây danh mục tối đa 2 cấp
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_categories_validate()
RETURNS TRIGGER AS $$
DECLARE
    v_parent categories%ROWTYPE;
BEGIN
    IF NEW.parent_category_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.parent_category_id = NEW.id THEN
        RAISE EXCEPTION 'Danh mục không thể là cha của chính nó'
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT * INTO v_parent FROM categories WHERE id = NEW.parent_category_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Danh mục cha không tồn tại'
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF v_parent.parent_category_id IS NOT NULL THEN
        RAISE EXCEPTION 'Danh mục chỉ được tối đa hai cấp (MAX_DEPTH_EXCEEDED)'
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_parent.type <> NEW.type THEN
        RAISE EXCEPTION 'Danh mục con phải cùng loại với cha (TYPE_MISMATCH_WITH_PARENT)'
            USING ERRCODE = 'check_violation';
    END IF;

    NEW.category_group_id := v_parent.category_group_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_categories_validate
    BEFORE INSERT OR UPDATE OF parent_category_id, type ON categories
    FOR EACH ROW EXECUTE FUNCTION fn_categories_validate();

CREATE OR REPLACE FUNCTION fn_categories_block_demote()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.parent_category_id IS NOT NULL
       AND OLD.parent_category_id IS NULL
       AND EXISTS (SELECT 1 FROM categories
                   WHERE parent_category_id = NEW.id AND NOT is_deleted) THEN
        RAISE EXCEPTION 'Danh mục đang có con, không thể chuyển thành cấp con (CATEGORY_HAS_CHILDREN)'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_categories_block_demote
    BEFORE UPDATE OF parent_category_id ON categories
    FOR EACH ROW EXECUTE FUNCTION fn_categories_block_demote();

-- -------------------------------------------------------------
-- 4. wallet_category_settings — Bật/tắt danh mục theo từng ví
-- -------------------------------------------------------------
CREATE TABLE wallet_category_settings (
    wallet_id   UUID    NOT NULL,
    category_id UUID    NOT NULL,
    is_enabled  BOOLEAN NOT NULL DEFAULT TRUE,

    PRIMARY KEY (wallet_id, category_id),
    CONSTRAINT fk_wcs_wallet   FOREIGN KEY (wallet_id)   REFERENCES wallets    (id) ON DELETE CASCADE,
    CONSTRAINT fk_wcs_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE
);

COMMENT ON TABLE wallet_category_settings IS
    'Danh mục bị TẮT ở một ví cụ thể. Vắng dòng = đang bật. Truy vấn đọc dùng LEFT JOIN + COALESCE(is_enabled, TRUE)';

-- =============================================================
-- SEED DATA: Kho biểu tượng, Nhóm lớn và Danh mục mặc định
-- =============================================================
INSERT INTO icons (code, display_name, path_data, icon_group, search_keywords, sort_order) VALUES
-- Ăn uống
('an_uong',    'Bát đũa',      'M8 3v7a2 2 0 002 2v9M16 3c-1.4 1.2-2 2.6-2 4.4 0 1.6.6 3 2 4.1v9', 'an_uong', 'ăn uống cơm phở bún nhà hàng quán', 1),
('ca_phe',     'Ly cà phê',    'M6 8h12v6a4 4 0 01-4 4h-4a4 4 0 01-4-4zM18 10h2a2 2 0 010 4h-2',   'an_uong', 'cà phê cafe trà sữa nước uống',      2),
('banh_mi',    'Bánh mì',      'M4 12a8 4 0 0116 0v4a2 2 0 01-2 2H6a2 2 0 01-2-2z',                'an_uong', 'bánh mì ăn sáng đồ ăn nhanh',        3),
('bia_ruou',   'Ly bia',       'M7 5h8v14a2 2 0 01-2 2H9a2 2 0 01-2-2zM15 8h2a2 2 0 012 2v4a2 2 0 01-2 2h-2', 'an_uong', 'bia rượu nhậu',        4),
('sieu_thi',   'Giỏ hàng',     'M4 7h16l-1.5 10.5a2 2 0 01-2 1.5H7.5a2 2 0 01-2-1.5zM9 7a3 3 0 016 0', 'an_uong', 'siêu thị đi chợ thực phẩm',       5),
-- Đi lại
('xe_may',     'Xe máy',       'M5 17a2.5 2.5 0 100-5 2.5 2.5 0 000 5zM19 17a2.5 2.5 0 100-5 2.5 2.5 0 000 5zM7 14.5h6l3-5h3', 'di_lai', 'xe máy moto đi lại', 10),
('o_to',       'Ô tô',         'M3.5 13l1.9-4.6A2 2 0 017.3 7h9.4a2 2 0 011.9 1.4L20.5 13v5h-2.7v-1.8H6.2V18H3.5zM7 15.5h.01M17 15.5h.01', 'di_lai', 'ô tô xe hơi taxi grab', 11),
('xang_dau',   'Bình xăng',    'M5 20V5a2 2 0 012-2h5a2 2 0 012 2v15M4 20h11M14 9h3a2 2 0 012 2v5a1.5 1.5 0 003 0v-6l-2.5-3', 'di_lai', 'xăng dầu nhiên liệu', 12),
('xe_bus',     'Xe buýt',      'M4 6a2 2 0 012-2h12a2 2 0 012 2v10H4zM4 16v2h3v-2M17 16v2h3v-2M4 10h16', 'di_lai', 'xe buýt bus vé tàu', 13),
-- Mua sắm
('mua_sam',    'Túi mua sắm',  'M6 7.5h12l-1.1 12.5H7.1L6 7.5zM9.2 7.5a2.8 2.8 0 015.6 0',         'mua_sam', 'mua sắm shopping shopee lazada',   20),
('quan_ao',    'Áo',           'M8 4l-4 3 2 3 2-1v11h8V9l2 1 2-3-4-3-2 2h-4z',                     'mua_sam', 'quần áo thời trang giày dép',      21),
('dien_thoai', 'Điện thoại',   'M7 3h10a1 1 0 011 1v16a1 1 0 01-1 1H7a1 1 0 01-1-1V4a1 1 0 011-1zM10 18h4', 'mua_sam', 'điện thoại đồ điện tử', 22),
('qua_tang',   'Hộp quà',      'M4 9h16v11H4zM4 9l2-4h5v4M20 9l-2-4h-5v4M12 5v15',                 'mua_sam', 'quà tặng sinh nhật biếu',          23),
-- Giải trí
('giai_tri',   'Màn hình',     'M4 6.5h16v11H4zM10 10l5.5 3-5.5 3z',                               'giai_tri', 'giải trí phim ảnh xem',           30),
('game',       'Tay cầm',      'M6 9h12a4 4 0 014 4v1a3 3 0 01-5.2 2L15 15H9l-1.8 1A3 3 0 012 14v-1a4 4 0 014-4z', 'giai_tri', 'game trò chơi', 31),
('du_lich',    'Máy bay',      'M2 14l9-2.5V5a1.5 1.5 0 013 0v6.5L23 14v2l-9-1.5V19l2.5 1.5v1.5L12 21l-4.5 1v-1.5L10 19v-4.5L1 16z', 'giai_tri', 'du lịch máy bay nghỉ mát', 32),
('the_thao',   'Quả bóng',     'M12 3a9 9 0 100 18 9 9 0 000-18zM12 3v18M3 12h18',                 'giai_tri', 'thể thao bóng đá gym',            33),
-- Nhà ở
('nha',        'Ngôi nhà',     'M3.5 11L12 4.5l8.5 6.5M6.4 9.6V20h11.2V9.6',                       'khac',    'nhà ở thuê nhà chung cư',          40),
('dien',       'Tia sét',      'M13 2L4.5 13H11l-1 9 8.5-11H12z',                                  'khac',    'điện tiền điện hoá đơn',           41),
('nuoc',       'Giọt nước',    'M12 3.5C12 3.5 5.5 10 5.5 14a6.5 6.5 0 0013 0c0-4-6.5-10.5-6.5-10.5z', 'khac', 'nước tiền nước hoá đơn',        42),
('internet',   'Sóng wifi',    'M2.5 8.5a14 14 0 0119 0M5.5 12a10 10 0 0113 0M8.5 15.5a6 6 0 017 0M12 19h.01', 'khac', 'internet wifi mạng', 43),
-- Sức khoẻ
('suc_khoe',   'Túi y tế',     'M4.5 8h15v11h-15zM9 8V5.5h6V8M12 11.5v4M10 13.5h4',                'suc_khoe', 'sức khoẻ bệnh viện khám bác sĩ',  50),
('thuoc',      'Viên thuốc',   'M8.5 4a4.5 4.5 0 014.5 4.5v7a4.5 4.5 0 01-9 0v-7A4.5 4.5 0 018.5 4zM4 12h9', 'suc_khoe', 'thuốc nhà thuốc', 51),
-- Học tập
('hoc_tap',    'Mũ tốt nghiệp','M3 8.4L12 4.5l9 3.9-9 3.9-9-3.9zM7 11.2V16c0 1.1 2.2 2 5 2s5-.9 5-2v-4.8', 'hoc_tap', 'học tập trường lớp khoá học', 60),
('sach',       'Quyển sách',   'M5 4h11a2 2 0 012 2v14H7a2 2 0 01-2-2zM7 20a2 2 0 01-2-2',         'hoc_tap',  'sách vở tài liệu',                61),
-- Tài chính
('luong',      'Phong bì tiền','M4 7.5h16v10H4zM4 7.5l12-3v3M12 15a2 2 0 100-4 2 2 0 000 4z',      'tai_chinh', 'lương thu nhập tiền công',       70),
('thuong',     'Ngôi sao',     'M12 4.5l2.2 4.4 4.8.7-3.5 3.4.8 4.8-4.3-2.3-4.3 2.3.8-4.8L5 9.6l4.8-.7z', 'tai_chinh', 'thưởng bonus', 71),
('dau_tu',     'Biểu đồ cột',  'M4 18.5h16M6.5 15V9.5M11 15V6M15.5 15v-4M20 15V8',                 'tai_chinh', 'đầu tư chứng khoán lãi',         72),
('ngan_hang',  'Toà nhà',      'M3 9l9-5 9 5v2H3zM5 11v7M10 11v7M14 11v7M19 11v7M3 20h18',         'tai_chinh', 'ngân hàng tài khoản',            73),
('vi_tien',    'Ví',           'M3 7a2 2 0 012-2h13v3M3 7v11a2 2 0 002 2h14a1 1 0 001-1v-3M18 11h4v4h-4a2 2 0 010-4z', 'tai_chinh', 'ví tiền mặt', 74),
('the',        'Thẻ',          'M3 6h18v12H3zM3 10h18M6 14h4',                                     'tai_chinh', 'thẻ tín dụng credit',            75),
('cho_vay',    'Bắt tay',      'M6 12l3-3 3 2 3-3 3 3M4 15h16',                                    'tai_chinh', 'cho vay nợ mượn',                76),
('tiet_kiem',  'Heo đất',      'M4 12a6 6 0 016-6h4a6 6 0 016 6v3h-2v2h-3v-2H9v2H6v-2H4zM7 11h.01M20 12h2', 'tai_chinh', 'tiết kiệm mục tiêu', 77),
-- Khác
('khac',       'Dấu hỏi',      'M12 20a8 8 0 100-16 8 8 0 000 16zM12 15.5v.01M12 12.8c0-1.6 2-1.8 2-3.3a2 2 0 10-4 0', 'khac', 'khác chưa phân loại', 90),
('thiet_yeu',  'Vòng tròn',    'M12 21a9 9 0 100-18 9 9 0 000 18z',                                'khac',    'thiết yếu cơ bản',                 91),
('gia_dinh',   'Nhóm người',   'M9 11a3 3 0 100-6 3 3 0 000 6zM3 20a6 6 0 0112 0M17 11a3 3 0 100-6M18 20a6 6 0 00-2-4.5', 'khac', 'gia đình nhóm chung', 92)
ON CONFLICT (code) DO NOTHING;

INSERT INTO category_groups (name, color, icon_id, sort_order) VALUES
('Thiết yếu', '#4e9e76', (SELECT id FROM icons WHERE code = 'thiet_yeu'), 1),
('Giải trí',  '#8ecaa9', (SELECT id FROM icons WHERE code = 'giai_tri'),  2),
('Giáo dục',  '#c9a26b', (SELECT id FROM icons WHERE code = 'hoc_tap'),   3),
('Y tế',      '#9b968c', (SELECT id FROM icons WHERE code = 'suc_khoe'),  4),
('Khác',      '#4a463f', (SELECT id FROM icons WHERE code = 'khac'),      5)
ON CONFLICT (name) DO NOTHING;

-- Danh mục mặc định cấp CHA
INSERT INTO categories (user_id, parent_category_id, category_group_id, name, type, icon_id, color, sort_order)
SELECT NULL, NULL, g.id, v.name, v.type, i.id, v.color, v.sort_order
FROM (VALUES
    -- Danh mục CHI
    ('Ăn uống',           'expense', 'Thiết yếu', 'an_uong',   '#3d6b7d', 1),
    ('Đi lại',            'expense', 'Thiết yếu', 'xe_may',    '#4a6f5c', 2),
    ('Nhà ở & Hoá đơn',   'expense', 'Thiết yếu', 'nha',       '#2f8272', 3),
    ('Mua sắm',           'expense', 'Giải trí',  'mua_sam',   '#7a5f8a', 4),
    ('Giải trí',          'expense', 'Giải trí',  'giai_tri',  '#3a6ea5', 5),
    ('Sức khoẻ',          'expense', 'Y tế',      'suc_khoe',  '#8a5b52', 6),
    ('Học tập',           'expense', 'Giáo dục',  'hoc_tap',   '#4a7f8a', 7),
    ('Cho vay',           'expense', 'Khác',      'cho_vay',   '#5b6b7a', 8),
    ('Trả nợ',            'expense', 'Khác',      'the',       '#6b5b7a', 9),
    ('Khác',              'expense', 'Khác',      'khac',      '#5b5b5b', 10),
    -- Danh mục THU
    ('Lương',             'income',  'Khác',      'luong',     '#3f7d55', 20),
    ('Thưởng',            'income',  'Khác',      'thuong',    '#8a7a3f', 21),
    ('Đầu tư',            'income',  'Khác',      'dau_tu',    '#3f6b8a', 22),
    ('Thu nợ',            'income',  'Khác',      'cho_vay',   '#4a7f6b', 23),
    ('Đi vay',            'income',  'Khác',      'ngan_hang', '#7a6b4a', 24),
    ('Khác',              'income',  'Khác',      'khac',      '#5b5b5b', 25)
) AS v(name, type, group_name, icon_code, color, sort_order)
JOIN category_groups g ON g.name = v.group_name
JOIN icons           i ON i.code = v.icon_code
ON CONFLICT DO NOTHING;

-- Danh mục CON mẫu
INSERT INTO categories (user_id, parent_category_id, category_group_id, name, type, icon_id, color, sort_order)
SELECT NULL, p.id, p.category_group_id, v.name, p.type, i.id, p.color, v.sort_order
FROM (VALUES
    ('Ăn uống',         'Ăn sáng',        'banh_mi',   1),
    ('Ăn uống',         'Ăn trưa',        'an_uong',   2),
    ('Ăn uống',         'Ăn tối',         'an_uong',   3),
    ('Ăn uống',         'Cà phê',         'ca_phe',    4),
    ('Ăn uống',         'Ăn vặt',         'banh_mi',   5),
    ('Ăn uống',         'Đi chợ',         'sieu_thi',  6),
    ('Đi lại',          'Xăng xe',        'xang_dau',  1),
    ('Đi lại',          'Taxi, Grab',     'o_to',      2),
    ('Đi lại',          'Gửi xe',         'xe_may',    3),
    ('Đi lại',          'Vé tàu xe',      'xe_bus',    4),
    ('Nhà ở & Hoá đơn', 'Tiền thuê nhà',  'nha',       1),
    ('Nhà ở & Hoá đơn', 'Điện',           'dien',      2),
    ('Nhà ở & Hoá đơn', 'Nước',           'nuoc',      3),
    ('Nhà ở & Hoá đơn', 'Internet',       'internet',  4),
    ('Nhà ở & Hoá đơn', 'Điện thoại',     'dien_thoai',5),
    ('Mua sắm',         'Quần áo',        'quan_ao',   1),
    ('Mua sắm',         'Đồ điện tử',     'dien_thoai',2),
    ('Mua sắm',         'Quà tặng',       'qua_tang',  3),
    ('Giải trí',        'Xem phim',       'giai_tri',  1),
    ('Giải trí',        'Du lịch',        'du_lich',   2),
    ('Giải trí',        'Thể thao',       'the_thao',  3),
    ('Sức khoẻ',        'Thuốc',          'thuoc',     1),
    ('Sức khoẻ',        'Khám bệnh',      'suc_khoe',  2),
    ('Học tập',         'Sách vở',        'sach',      1),
    ('Học tập',         'Khoá học',       'hoc_tap',   2)
) AS v(parent_name, name, icon_code, sort_order)
JOIN categories p ON p.name = v.parent_name
                 AND p.user_id IS NULL
                 AND p.parent_category_id IS NULL
                 AND p.type = 'expense'
JOIN icons      i ON i.code = v.icon_code
ON CONFLICT DO NOTHING;

-- Danh mục hệ thống cho điều chỉnh kiểm kê ví (V8)
INSERT INTO categories (user_id, parent_category_id, category_group_id, name, type, icon_id, color, sort_order)
SELECT NULL, NULL, g.id, v.name, v.type, i.id, v.color, v.sort_order
FROM (VALUES
    ('Cập nhật số dư', 'expense', 'Khác', 'vi_tien', '#6b6b6b', 11),
    ('Cập nhật số dư', 'income',  'Khác', 'vi_tien', '#6b6b6b', 26)
) AS v(name, type, group_name, icon_code, color, sort_order)
JOIN category_groups g ON g.name = v.group_name
JOIN icons           i ON i.code = v.icon_code
ON CONFLICT DO NOTHING;
