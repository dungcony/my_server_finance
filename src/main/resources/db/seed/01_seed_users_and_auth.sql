-- =============================================================
-- Dữ liệu mẫu (Seed Data) - Phần 1: Người dùng, Vai trò & Phiên đăng nhập
-- Quy mô: 100.000 Tài khoản người dùng (Users)
-- UUID có quy luật cố định: 00000000-0000-0000-0000-XXXXXXXXXXXX
-- Phù hợp với Schema migration_v2 (V1__auth, V6__trigger_wallet_default)
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

SET work_mem = '64MB';

-- =============================================================
-- 1. SINH 100.000 TÀI KHOẢN NGƯỜI DÙNG
-- Mật khẩu mặc định: Admin@123 (bcrypt cost 12)
-- Lưu ý: Khi INSERT vào bảng users, trigger trg_users_default_wallet (V6)
-- sẽ tự động khởi tạo 1 ví mặc định 'Tiền mặt' (CASH) cho mỗi người dùng.
-- =============================================================
INSERT INTO users (
    id,
    email,
    password_hash,
    first_name,
    last_name,
    plan,
    status,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    ('00000000-0000-0000-0000-' || LPAD(s::TEXT, 12, '0'))::UUID AS id,
    'user' || s || '@financeapp.com' AS email,
    '$2a$12$7humJ1hfArXzq9mpOqwJLuY46gx7wAgg4RJgu8Ht3vi/Qwb4qx60G' AS password_hash,
    (ARRAY['Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan', 'Vũ', 'Đặng', 'Bùi'])[1 + (s % 10)] AS first_name,
    'User ' || s AS last_name,
    CASE WHEN s % 10 = 0 THEN 'PREMIUM' ELSE 'FREE' END AS plan,
    CASE 
        WHEN s % 100 = 0 THEN 'BLOCKED' 
        WHEN s % 50 = 0  THEN 'PENDING_VERIFY' 
        ELSE 'ACTIVE' 
    END AS status,
    false AS is_deleted,
    now() - ((s % 365) || ' days')::INTERVAL AS created_at,
    now() - ((s % 365) || ' days')::INTERVAL AS updated_at
FROM generate_series(1, 100000) AS s
ON CONFLICT (email) DO NOTHING;

-- =============================================================
-- 2. GÁN VAI TRÒ ROLE_USER CHO TOÀN BỘ 100.000 TÀI KHOẢN
-- =============================================================
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email LIKE 'user%@financeapp.com'
  AND r.name = 'ROLE_USER'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- =============================================================
-- 3. TẠO PHIÊN ĐĂNG NHẬP (REFRESH TOKENS) CHO 10.000 TÀI KHOẢN ACTIVE
-- Mô phỏng các phiên người dùng online trên Mobile và Web
-- =============================================================
INSERT INTO refresh_tokens (
    id,
    user_id,
    email,
    token_hash,
    expires_at,
    device_info,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    u.id,
    u.email,
    encode(digest(u.email || '_salt_token_' || u.rn, 'sha256'), 'hex'),
    now() + interval '30 days',
    (ARRAY[
        'iPhone 15 Pro / iOS 17.5 (App)',
        'Samsung Galaxy S24 Ultra / Android 14 (App)',
        'Chrome 125.0 / Windows 11 (Web)',
        'Safari 17.4 / macOS Sonoma (Web)',
        'Xiaomi 14 / HyperOS (App)'
    ])[1 + (u.rn % 5)],
    false,
    now() - ((u.rn % 15) || ' days')::INTERVAL,
    now() - ((u.rn % 15) || ' days')::INTERVAL
FROM (
    SELECT id, email, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM users
    WHERE email LIKE 'user%@financeapp.com'
      AND status = 'ACTIVE'
    LIMIT 10000
) u
ON CONFLICT (token_hash) DO NOTHING;

-- =============================================================
-- 4. NHẬT KÝ ĐĂNG NHẬP MẪU (LOGIN ATTEMPTS)
-- Phục vụ thống kê bảo mật và kiểm tra rate limit
-- =============================================================
INSERT INTO login_attempts (
    id,
    email,
    ip_address,
    succeeded,
    user_agent,
    attempted_at,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'user' || (1 + (s % 500)) || '@financeapp.com',
    ('192.168.1.' || (1 + (s % 254)))::INET,
    (s % 7 <> 0), -- Đa số thành công, một số thất bại
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0.0.0',
    now() - ((s % 30) || ' days')::INTERVAL,
    false,
    now() - ((s % 30) || ' days')::INTERVAL,
    now() - ((s % 30) || ' days')::INTERVAL
FROM generate_series(1, 2000) AS s;
