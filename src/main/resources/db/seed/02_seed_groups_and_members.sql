-- =============================================================
-- Dữ liệu mẫu (Seed Data) - Phần: Nhóm, Thành viên và Quỹ nhóm
-- Gồm: 1 Nhóm Siêu Lớn (100 thành viên) và 10.000 Nhóm thông thường
-- Yêu cầu: Đã chạy 01_seed_users_and_auth.sql trước
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

-- =============================================================
-- KHỐI DỮ LIỆU: NHÓM SIÊU LỚN (SUPER GROUP CHO STRESS TEST)
-- 100 thành viên (user1 đến user100), 1 quỹ nhóm
-- =============================================================

-- Tạo nhóm siêu lớn
INSERT INTO groups (
    id,
    name,
    description,
    invite_code,
    status,
    target,
    is_settlement_enabled,
    is_join_without_confirm,
    created_at,
    updated_at
) VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'Nhóm Siêu Lớn Stress Test (100 Thành Viên)',
    'Nhóm chứa 100 thành viên và 1 triệu giao dịch phục vụ đo tải cực hạn',
    'SUPERGROUP100',
    'ACTIVE',
    50000000000,
    true,
    true,
    now() - interval '365 days',
    now()
)
ON CONFLICT (invite_code) DO NOTHING;

-- Gán 100 thành viên (user1 đến user100) vào nhóm siêu lớn
INSERT INTO group_members (group_id, user_id, role, status, joined_at)
SELECT
    'c0000000-0000-0000-0000-000000000001',
    ('00000000-0000-0000-0000-' || LPAD(s::TEXT, 12, '0'))::UUID,
    CASE WHEN s = 1 THEN 'OWNER' ELSE 'MEMBER' END,
    'ACTIVE',
    now() - interval '360 days'
FROM generate_series(1, 100) AS s
ON CONFLICT DO NOTHING;

-- Khởi tạo quỹ nhóm cho nhóm siêu lớn (keepper là user1)
INSERT INTO group_funds (
    id,
    group_id,
    keepper_id,
    current_balance,
    created_at
) VALUES (
    'd0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    100000000,
    now() - interval '365 days'
)
ON CONFLICT (group_id) DO UPDATE SET
    current_balance = EXCLUDED.current_balance,
    keepper_id = EXCLUDED.keepper_id;

-- =============================================================
-- KHỐI DỮ LIỆU: 10.000 NHÓM THÔNG THƯỜNG (PHÂN BỔ TỰ NHIÊN)
-- Đảm bảo toàn bộ 100.000 user đều có nhóm tham gia khi online
-- =============================================================

-- Sinh 10.000 nhóm thông thường
INSERT INTO groups (
    id,
    name,
    description,
    invite_code,
    status,
    target,
    is_settlement_enabled,
    is_join_without_confirm,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'Nhóm sinh hoạt ' || s,
    'Nhóm chia sẻ chi phí số ' || s,
    'INV' || LPAD(s::TEXT, 8, '0'),
    'ACTIVE',
    20000000,
    true,
    true,
    now() - interval '180 days',
    now()
FROM generate_series(1, 10000) AS s
ON CONFLICT (invite_code) DO NOTHING;

-- Phân bổ thành viên cho 10.000 nhóm (mỗi nhóm có 3 thành viên liền kề)
INSERT INTO group_members (group_id, user_id, role, status, joined_at)
SELECT
    g.id,
    ('00000000-0000-0000-0000-' || LPAD((((g.group_seq - 1) * 3 + m - 1) % 100000 + 1)::TEXT, 12, '0'))::UUID,
    CASE WHEN m = 1 THEN 'OWNER' ELSE 'MEMBER' END,
    'ACTIVE',
    now() - interval '150 days'
FROM (
    SELECT id, ROW_NUMBER() OVER () AS group_seq
    FROM groups
    WHERE invite_code LIKE 'INV%'
) g
CROSS JOIN generate_series(1, 3) AS m
ON CONFLICT DO NOTHING;

-- Khởi tạo quỹ cho 10.000 nhóm thông thường
INSERT INTO group_funds (id, group_id, keepper_id, current_balance, created_at)
SELECT
    gen_random_uuid(),
    gm.group_id,
    gm.user_id,
    15000000,
    now() - interval '150 days'
FROM group_members gm
JOIN groups g ON g.id = gm.group_id
WHERE g.invite_code LIKE 'INV%'
  AND gm.role = 'OWNER'
ON CONFLICT (group_id) DO NOTHING;
