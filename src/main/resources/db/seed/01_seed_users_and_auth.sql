-- =============================================================
-- Dữ liệu mẫu (Seed Data) - Phần: User và Authentication
-- 100.000 User với UUID cố định, gán ROLE_USER và refresh token mẫu
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

-- Tăng work_mem tạm thời cho phiên thực thi để tăng tốc độ nạp dữ liệu lớn
SET work_mem = '64MB';

-- Sinh 100.000 tài khoản người dùng độc lập
-- Mật khẩu mặc định: Admin@123
INSERT INTO users (
    id,
    email,
    password_hash,
    first_name,
    last_name,
    plan,
    status,
    role,
    is_confirm,
    is_blocked,
    is_deleted,
    created_at
)
SELECT
    ('00000000-0000-0000-0000-' || LPAD(s::TEXT, 12, '0'))::UUID,
    'user' || s || '@financeapp.com',
    '$2a$12$7humJ1hfArXzq9mpOqwJLuY46gx7wAgg4RJgu8Ht3vi/Qwb4qx60G',
    'Thành viên',
    'Số ' || s,
    'free',
    'active',
    'USER',
    true,
    false,
    false,
    now()
FROM generate_series(1, 100000) AS s
ON CONFLICT (email) DO NOTHING;

-- Gán vai trò ROLE_USER cho toàn bộ tài khoản vừa sinh
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email LIKE 'user%@financeapp.com'
  AND r.name = 'ROLE_USER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- Tạo phiên đăng nhập mẫu (refresh_tokens) cho 10 tài khoản đầu tiên
INSERT INTO refresh_tokens (
    id,
    user_id,
    email,
    token_hash,
    expires_at,
    device_info,
    created_at
)
SELECT
    gen_random_uuid(),
    u.id,
    u.email,
    'seed_token_hash_' || u.email || '_test_30days_valid_key',
    now() + interval '30 days',
    'Test Runner / Seed Device',
    now()
FROM users u
WHERE u.email IN (
    'user1@financeapp.com', 'user2@financeapp.com', 'user3@financeapp.com',
    'user4@financeapp.com', 'user5@financeapp.com', 'user6@financeapp.com',
    'user7@financeapp.com', 'user8@financeapp.com', 'user9@financeapp.com',
    'user10@financeapp.com'
)
ON CONFLICT (token_hash) DO NOTHING;
