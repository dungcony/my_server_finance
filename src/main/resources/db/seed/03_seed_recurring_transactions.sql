-- =============================================================
-- Dữ liệu mẫu (Seed Data) - Phần 3: Giao dịch định kỳ & Lịch phát sinh
-- Quy mô: Cấu hình gói dịch vụ thuê bao định kỳ (Subscriptions) & Lịch chạy
-- Phù hợp với Schema migration_v2 (V3__wallet_transaction_recurring_recurring_scheduler_icon)
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

SET work_mem = '64MB';

-- =============================================================
-- 1. THIẾT LẬP GIAO DỊCH ĐỊNH KỲ (RECURRING TRANSACTIONS)
-- Cho 2.000 người dùng tích cực đầu tiên
-- =============================================================

-- Gói dịch vụ định kỳ mẫu
WITH user_wallets AS (
    SELECT w.id AS wallet_id, w.user_id, (SUBSTRING(u.email FROM 'user([0-9]+)@')::INT) AS u_idx
    FROM wallets w
    JOIN users u ON u.id = w.user_id
    WHERE w.is_default = TRUE
      AND (SUBSTRING(u.email FROM 'user([0-9]+)@')::INT) <= 2000
)
INSERT INTO recurring_transactions (
    id,
    user_id,
    wallet_id,
    category_code,
    name,
    type,
    amount,
    note,
    start_date,
    end_date,
    frequency,
    repeat_interval,
    is_enabled,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    uw.user_id,
    uw.wallet_id,
    tmpl.category_code,
    tmpl.name,
    tmpl.type,
    tmpl.amount,
    tmpl.note,
    CURRENT_DATE - INTERVAL '90 days',
    NULL, -- Lặp vô hạn
    tmpl.frequency,
    1,
    true,
    false,
    now() - interval '90 days',
    now() - interval '90 days'
FROM user_wallets uw
CROSS JOIN (
    VALUES
    ('xem_phim',      'Gói thuê bao Netflix Standard', 'EXPENSE', 260000::BIGINT,  'Tự động gia hạn thẻ', 'MONTH'),
    ('internet',      'Cước Internet FPT gia đình',    'EXPENSE', 250000::BIGINT,  'Thanh toán ngày 15 hàng tháng', 'MONTH'),
    ('the_thao',      'Thẻ tập Gym California',        'EXPENSE', 650000::BIGINT,  'Gói tập tháng', 'MONTH'),
    ('tien_thue_nha', 'Tiền thuê căn hộ chung cư',     'EXPENSE', 6000000::BIGINT, 'Chuyển khoản chủ nhà đầu tháng', 'MONTH'),
    ('luong',         'Thu nhập tiền lương dự kiến',    'INCOME',  25000000::BIGINT,'Lương cơ bản chuyển vào ví', 'MONTH')
) AS tmpl(category_code, name, type, amount, note, frequency);

-- =============================================================
-- 2. TẠO CÁC BUỔI LỊCH PHÁT SINH (RECURRING SCHEDULES)
-- Bao gồm các buổi đã chạy thành công trong quá khứ (SUCCESS)
-- và các buổi sắp tới trong tương lai (PENDING) để phục vụ test scheduler
-- =============================================================

-- Buổi quá khứ (SUCCESS) trong 3 tháng qua
INSERT INTO recurring_transaction_schedules (
    id,
    recurring_id,
    user_id,
    run_date,
    amount,
    status,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    r.id,
    r.user_id,
    (r.start_date + (m || ' months')::INTERVAL)::DATE,
    r.amount,
    'SUCCESS',
    false,
    now() - interval '90 days',
    now() - interval '90 days'
FROM recurring_transactions r
CROSS JOIN generate_series(0, 2) AS m;

-- Buổi lịch hiện tại & tương lai (PENDING) để Scheduler quét tự động
INSERT INTO recurring_transaction_schedules (
    id,
    recurring_id,
    user_id,
    run_date,
    amount,
    status,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    r.id,
    r.user_id,
    (r.start_date + ((3 + fut) || ' months')::INTERVAL)::DATE,
    r.amount,
    'PENDING',
    false,
    now(),
    now()
FROM recurring_transactions r
CROSS JOIN generate_series(0, 2) AS fut;
