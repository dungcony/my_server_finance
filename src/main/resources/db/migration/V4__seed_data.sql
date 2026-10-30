-- =============================================================
-- V4 — Dữ liệu khởi tạo hệ thống (Master Seed Data)
-- =============================================================

INSERT INTO categories (code, name, icon_id, color, level, path)
SELECT v.code, v.name, i.id, v.color, 0, v.code
FROM (VALUES
    ('gop_quy_nhom',        'Góp quỹ nhóm',           'gia_dinh', '#2f8272'),
    ('nhan_hoan_tien_nhom', 'Nhận hoàn tiền từ nhóm', 'vi_tien',  '#4a7f6b')
) AS v(code, name, icon_code, color)
JOIN icons i ON i.code = v.icon_code;

-- =============================================================
-- KHO BIỂU TƯỢNG HỆ THỐNG (ICONS)
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

-- =============================================================
-- DANH MỤC THU CHI MẶC ĐỊNH (CATEGORIES)
-- Cây nhiều cấp, không phân biệt thu/chi: không có cha thì là gốc.
-- level, path, is_leaf do service duy trì khi chạy; seed tự tính cho khớp.
-- =============================================================

-- Danh mục gốc: level 0, path = code, mặc định là lá
INSERT INTO categories (code, name, icon_id, color, level, path)
SELECT v.code, v.name, i.id, v.color, 0, v.code
FROM (VALUES
    ('an_uong',        'Ăn uống',          'an_uong',   '#3d6b7d'),
    ('di_lai',         'Đi lại',           'xe_may',    '#4a6f5c'),
    ('nha_o_hoa_don',  'Nhà ở & Hoá đơn',  'nha',       '#2f8272'),
    ('mua_sam',        'Mua sắm',          'mua_sam',   '#7a5f8a'),
    ('giai_tri',       'Giải trí',         'giai_tri',  '#3a6ea5'),
    ('suc_khoe',       'Sức khoẻ',         'suc_khoe',  '#8a5b52'),
    ('hoc_tap',        'Học tập',          'hoc_tap',   '#4a7f8a'),
    ('cho_vay',        'Cho vay',          'cho_vay',   '#5b6b7a'),
    ('tra_no',         'Trả nợ',           'the',       '#6b5b7a'),
    ('khac',           'Khác',             'khac',      '#5b5b5b'),
    ('luong',          'Lương',            'luong',     '#3f7d55'),
    ('thuong',         'Thưởng',           'thuong',    '#8a7a3f'),
    ('dau_tu',         'Đầu tư',           'dau_tu',    '#3f6b8a'),
    ('thu_no',         'Thu nợ',           'cho_vay',   '#4a7f6b'),
    ('di_vay',         'Đi vay',           'ngan_hang', '#7a6b4a'),
    ('cap_nhat_so_du', 'Cập nhật số dư',   'vi_tien',   '#6b6b6b')
) AS v(code, name, icon_code, color)
JOIN icons i ON i.code = v.icon_code;

-- Danh mục con: level = level cha + 1, path = path cha + '/' + code, màu thừa hưởng từ cha
INSERT INTO categories (code, name, parent_id, icon_id, color, level, path)
SELECT v.code, v.name, p.id, i.id, p.color, p.level + 1, p.path || '/' || v.code
FROM (VALUES
    ('an_uong',       'an_sang',       'Ăn sáng',       'banh_mi'),
    ('an_uong',       'an_trua',       'Ăn trưa',       'an_uong'),
    ('an_uong',       'an_toi',        'Ăn tối',        'an_uong'),
    ('an_uong',       'ca_phe',        'Cà phê',        'ca_phe'),
    ('an_uong',       'an_vat',        'Ăn vặt',        'banh_mi'),
    ('an_uong',       'di_cho',        'Đi chợ',        'sieu_thi'),
    ('di_lai',        'xang_xe',       'Xăng xe',       'xang_dau'),
    ('di_lai',        'taxi_grab',     'Taxi, Grab',    'o_to'),
    ('di_lai',        'gui_xe',        'Gửi xe',        'xe_may'),
    ('di_lai',        've_tau_xe',     'Vé tàu xe',     'xe_bus'),
    ('nha_o_hoa_don', 'tien_thue_nha', 'Tiền thuê nhà', 'nha'),
    ('nha_o_hoa_don', 'dien',          'Điện',          'dien'),
    ('nha_o_hoa_don', 'nuoc',          'Nước',          'nuoc'),
    ('nha_o_hoa_don', 'internet',      'Internet',      'internet'),
    ('nha_o_hoa_don', 'dien_thoai',    'Điện thoại',    'dien_thoai'),
    ('mua_sam',       'quan_ao',       'Quần áo',       'quan_ao'),
    ('mua_sam',       'do_dien_tu',    'Đồ điện tử',    'dien_thoai'),
    ('mua_sam',       'qua_tang',      'Quà tặng',      'qua_tang'),
    ('giai_tri',      'xem_phim',      'Xem phim',      'giai_tri'),
    ('giai_tri',      'du_lich',       'Du lịch',       'du_lich'),
    ('giai_tri',      'the_thao',      'Thể thao',      'the_thao'),
    ('suc_khoe',      'thuoc',         'Thuốc',         'thuoc'),
    ('suc_khoe',      'kham_benh',     'Khám bệnh',     'suc_khoe'),
    ('hoc_tap',       'sach_vo',       'Sách vở',       'sach'),
    ('hoc_tap',       'khoa_hoc',      'Khoá học',      'hoc_tap')
) AS v(parent_code, code, name, icon_code)
JOIN categories p ON p.code = v.parent_code
JOIN icons      i ON i.code = v.icon_code;

-- Danh mục đã có con thì không còn là lá
UPDATE categories SET is_leaf = FALSE
WHERE id IN (SELECT parent_id FROM categories WHERE parent_id IS NOT NULL);

-- =============================================================
-- KHỐI VAI TRÒ & PHÂN QUYỀN (ROLES & PERMISSIONS)
-- =============================================================
INSERT INTO roles (name, level, description) VALUES
    ('ROLE_ADMIN', 1,  'Quản trị viên toàn quyền hệ thống'),
    ('ROLE_USER',  10, 'Người dùng ứng dụng thông thường')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, description) VALUES
    ('users:read',             'Xem danh sách và chi tiết người dùng'),
    ('users:update',           'Khóa, mở khóa hoặc cập nhật thông tin người dùng'),
    ('users:delete',           'Xoá tài khoản người dùng'),
    ('roles:read',             'Xem danh sách vai trò và quyền hạn'),
    ('roles:update',           'Sửa thông tin vai trò (tên, mô tả, cấp bậc)'),
    ('roles:delete',           'Xoá vai trò'),
    ('user_role:read',         'Xem vai trò đã gán cho người dùng'),
    ('user_role:update',       'Gán vai trò cho người dùng'),
    ('user_role:delete',       'Thu hồi vai trò đã gán của người dùng'),
    ('role_permission:read',   'Xem quyền hạn đã gán cho vai trò'),
    ('role_permission:update', 'Gán quyền hạn cho vai trò'),
    ('role_permission:delete', 'Thu hồi quyền hạn đã gán của vai trò'),
    ('categories:manage',      'Quản lý danh mục và biểu tượng mặc định hệ thống'),
    ('system:view_stats',      'Xem báo cáo thống kê toàn hệ thống')
ON CONFLICT (name) DO NOTHING;

-- Gán toàn bộ quyền quản trị cho ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- =============================================================
-- TÀI KHOẢN QUẢN TRỊ VIÊN MẶC ĐỊNH
-- =============================================================
INSERT INTO users (
    email,
    password_hash,
    first_name,
    last_name,
    plan,
    status,
    is_deleted,
    created_at
) VALUES (
    'admin@financeapp.com',
    '$2a$12$7humJ1hfArXzq9mpOqwJLuY46gx7wAgg4RJgu8Ht3vi/Qwb4qx60G',
    'Hệ thống',
    'Quản trị viên',
    'premium',
    'active',
    false,
    now()
)
ON CONFLICT (email) DO NOTHING;

-- Gán vai trò cho tài khoản Admin
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email = 'admin@financeapp.com'
  AND r.name IN ('ROLE_ADMIN', 'ROLE_USER')
ON CONFLICT (user_id, role_id) DO NOTHING;
