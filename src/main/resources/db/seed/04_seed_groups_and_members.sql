-- =============================================================
-- Dữ liệu mẫu (Seed Data) - Phần 4: Nhóm, Thành viên & Quỹ nhóm
-- Quy mô: 
--   - 1 Nhóm Siêu Lớn (100 thành viên user1 -> user100) phục vụ stress test
--   - 10.000 Nhóm thông thường phân bổ trên 100.000 người dùng
-- Phù hợp với Schema migration_v2 (V4__group)
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

SET work_mem = '64MB';

-- =============================================================
-- 1. TẠO NHÓM SIÊU LỚN (SUPER GROUP STRESS TEST)
-- UUID cố định: c0000000-0000-0000-0000-000000000001
-- =============================================================
INSERT INTO groups (
    id,
    name,
    description,
    invite_code,
    status,
    target,
    is_settlement_enabled,
    is_join_without_confirm,
    is_deleted,
    created_at,
    updated_at
) VALUES (
    'c0000000-0000-0000-0000-000000000001',
    'Nhóm Siêu Lớn Stress Test (100 Thành Viên)',
    'Nhóm chứa 100 thành viên phục vụ đo tải cực hạn và stress test hệ thống',
    'SUPER100',
    'ACTIVE',
    50000000000,
    true,
    true,
    false,
    now() - interval '365 days',
    now()
)
ON CONFLICT (invite_code) DO NOTHING;

-- Gán 100 thành viên (user1 đến user100) vào nhóm siêu lớn
INSERT INTO group_members (
    id,
    group_id,
    user_id,
    role,
    status,
    joined_at,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'c0000000-0000-0000-0000-000000000001',
    ('00000000-0000-0000-0000-' || LPAD(s::TEXT, 12, '0'))::UUID,
    CASE WHEN s = 1 THEN 'OWNER' ELSE 'MEMBER' END,
    'ACTIVE',
    now() - interval '360 days',
    false,
    now() - interval '360 days',
    now() - interval '360 days'
FROM generate_series(1, 100) AS s
ON CONFLICT DO NOTHING;

-- Khởi tạo quỹ cho nhóm siêu lớn (Thủ quỹ là user1)
INSERT INTO group_funds (
    id,
    group_id,
    keepper_id,
    current_balance,
    is_deleted,
    created_at,
    updated_at
) VALUES (
    'd0000000-0000-0000-0000-000000000001',
    'c0000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    100000000,
    false,
    now() - interval '365 days',
    now()
)
ON CONFLICT (group_id) DO UPDATE SET
    current_balance = EXCLUDED.current_balance,
    keepper_id = EXCLUDED.keepper_id;

-- =============================================================
-- 2. TẠO 10.000 NHÓM THÔNG THƯỜNG
-- Mã mời 8 ký tự (GXXXXXXX) khớp @Column(name = "invite_code", length = 8)
-- =============================================================
INSERT INTO groups (
    id,
    name,
    description,
    invite_code,
    status,
    target,
    is_settlement_enabled,
    is_join_without_confirm,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    (ARRAY['Nhóm sinh hoạt ', 'Gia đình nhỏ ', 'Hội bạn thân ', 'Phòng trọ ', 'Du lịch '])[1 + (s % 5)] || s,
    'Chia sẻ chi tiêu định kỳ số ' || s,
    'G' || LPAD(s::TEXT, 7, '0'),
    'ACTIVE',
    (10 + (s % 40)) * 1000000,
    true,
    true,
    false,
    now() - ((s % 180) || ' days')::INTERVAL,
    now() - ((s % 180) || ' days')::INTERVAL
FROM generate_series(1, 10000) AS s
ON CONFLICT (invite_code) DO NOTHING;

-- Phân bổ 3 thành viên liền kề cho mỗi nhóm (trải đều khắp 100.000 user)
INSERT INTO group_members (
    id,
    group_id,
    user_id,
    role,
    status,
    joined_at,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    g.id,
    ('00000000-0000-0000-0000-' || LPAD((((g.group_seq - 1) * 3 + m - 1) % 100000 + 1)::TEXT, 12, '0'))::UUID,
    CASE WHEN m = 1 THEN 'OWNER' ELSE 'MEMBER' END,
    'ACTIVE',
    now() - interval '150 days',
    false,
    now() - interval '150 days',
    now() - interval '150 days'
FROM (
    SELECT id, ROW_NUMBER() OVER (ORDER BY created_at, id) AS group_seq
    FROM groups
    WHERE invite_code LIKE 'G%'
) g
CROSS JOIN generate_series(1, 3) AS m
ON CONFLICT DO NOTHING;

-- Khởi tạo quỹ cho 10.000 nhóm thông thường (Chủ nhóm làm thủ quỹ)
INSERT INTO group_funds (
    id,
    group_id,
    keepper_id,
    current_balance,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    gm.group_id,
    gm.user_id,
    5000000,
    false,
    now() - interval '150 days',
    now()
FROM group_members gm
JOIN groups g ON g.id = gm.group_id
WHERE g.invite_code LIKE 'G%'
  AND gm.role = 'OWNER'
ON CONFLICT (group_id) DO NOTHING;
