-- =============================================================
-- Dữ liệu mẫu (Seed Data) - Phần 2: Ví bổ sung & Giao dịch cá nhân
-- Quy mô: Hàng chục nghìn ví phụ và hàng trăm nghìn giao dịch cá nhân thực tế
-- Phù hợp với Schema migration_v2 (V3__wallet_transaction, V7__trigger_wallet_balance, V11__trigger_category_leaf)
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

SET work_mem = '64MB';

-- =============================================================
-- 1. TẠO THÊM VÍ PHỤ CHO CÁC NGƯỜI DÙNG TÍCH CỰC (USER 1 ĐẾN USER 5.000)
-- Mỗi user có thêm 1 ví Ngân hàng và 1 Ví điện tử (is_default = false)
-- =============================================================
INSERT INTO wallets (
    id,
    user_id,
    name,
    type,
    balance,
    icon_code,
    color,
    sort_order,
    is_default,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    u.id,
    'Tài khoản Ngân hàng',
    'BANK',
    0,
    'ngan_hang',
    '#3f6b8a',
    1,
    false,
    false,
    u.created_at,
    u.created_at
FROM users u
WHERE u.email LIKE 'user%@financeapp.com'
  AND (SUBSTRING(u.email FROM 'user([0-9]+)@')::INT) <= 5000
ON CONFLICT DO NOTHING;

INSERT INTO wallets (
    id,
    user_id,
    name,
    type,
    balance,
    icon_code,
    color,
    sort_order,
    is_default,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    u.id,
    'Ví MoMo',
    'E_WALLET',
    0,
    'vi_tien',
    '#7a5f8a',
    2,
    false,
    false,
    u.created_at,
    u.created_at
FROM users u
WHERE u.email LIKE 'user%@financeapp.com'
  AND (SUBSTRING(u.email FROM 'user([0-9]+)@')::INT) <= 3000
ON CONFLICT DO NOTHING;

-- =============================================================
-- 2. NẠP HÀNG LOẠT GIAO DỊCH CÁ NHÂN (PERSONAL TRANSACTIONS)
-- Bao gồm Lương hàng tháng (INCOME) và các chi tiêu thường nhật (EXPENSE)
-- Toàn bộ danh mục đều là danh mục lá (is_leaf = true) tuân thủ trigger V11
-- =============================================================

-- Tạm thời ngắt trigger cộng trừ số dư để nạp dữ liệu tốc độ cao (bulk insert)
ALTER TABLE transactions DISABLE TRIGGER trg_transactions_wallet_balance;

-- Giao dịch thu nhập (Lương hàng tháng cho 5.000 người dùng trong 6 tháng gần nhất)
INSERT INTO transactions (
    id,
    user_id,
    wallet_id,
    category_code,
    type,
    amount,
    occurred_at,
    name,
    note,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    w.user_id,
    w.id,
    'luong',
    'INCOME',
    ((15 + (u_idx % 25)) * 1000000)::BIGINT, -- Lương 15 đến 40 triệu
    date_trunc('month', now() - (m || ' months')::INTERVAL) + interval '5 days 9 hours',
    'Lương tháng ' || to_char(now() - (m || ' months')::INTERVAL, 'MM/YYYY'),
    'Chuyển khoản lương định kỳ',
    false,
    date_trunc('month', now() - (m || ' months')::INTERVAL) + interval '5 days 9 hours',
    date_trunc('month', now() - (m || ' months')::INTERVAL) + interval '5 days 9 hours'
FROM (
    SELECT w.id, w.user_id, (SUBSTRING(u.email FROM 'user([0-9]+)@')::INT) AS u_idx
    FROM wallets w
    JOIN users u ON u.id = w.user_id
    WHERE w.name = 'Tài khoản Ngân hàng'
      AND (SUBSTRING(u.email FROM 'user([0-9]+)@')::INT) <= 5000
) w
CROSS JOIN generate_series(0, 5) AS m;

-- Giao dịch chi tiêu hàng ngày (EXPENSE) cho 3.000 người dùng tích cực nhất
INSERT INTO transactions (
    id,
    user_id,
    wallet_id,
    category_code,
    type,
    amount,
    occurred_at,
    name,
    note,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    w.user_id,
    w.id,
    cat.code,
    'EXPENSE',
    cat.base_amount + ((random() * 5)::INT * cat.step_amount),
    now() - ((s % 180) || ' days')::INTERVAL - ((s % 24) || ' hours')::INTERVAL,
    cat.name || ' ngày ' || to_char(now() - ((s % 180) || ' days')::INTERVAL, 'DD/MM'),
    'Chi tiêu sinh hoạt cá nhân',
    false,
    now() - ((s % 180) || ' days')::INTERVAL,
    now() - ((s % 180) || ' days')::INTERVAL
FROM (
    SELECT w.id, w.user_id
    FROM wallets w
    JOIN users u ON u.id = w.user_id
    WHERE (SUBSTRING(u.email FROM 'user([0-9]+)@')::INT) <= 3000
) w
CROSS JOIN LATERAL (
    SELECT 
        (v.cats)[1 + (floor(random() * array_length(v.cats, 1)))::INT] AS code,
        (v.names)[1 + (floor(random() * array_length(v.names, 1)))::INT] AS name,
        v.base_amount,
        v.step_amount
    FROM (
        VALUES 
        (
            ARRAY['an_sang', 'an_trua', 'an_toi', 'ca_phe', 'an_vat'],
            ARRAY['Bánh mì & Cà phê', 'Cơm trưa văn phòng', 'Ăn tối cùng bạn', 'Trà sữa chiều', 'Ăn vặt vỉa hè'],
            30000, 15000
        ),
        (
            ARRAY['di_cho', 'di_cho'],
            ARRAY['Đi chợ mua rau quả', 'Mua sắm thực phẩm siêu thị'],
            150000, 50000
        ),
        (
            ARRAY['xang_xe', 'taxi_grab'],
            ARRAY['Đổ xăng xe máy', 'Grab Car đi lại'],
            50000, 30000
        ),
        (
            ARRAY['tien_thue_nha', 'dien', 'nuoc', 'internet', 'dien_thoai'],
            ARRAY['Tiền phòng trọ', 'Thanh toán tiền điện', 'Tiền nước sinh hoạt', 'Cước internet cáp quang', 'Nạp thẻ điện thoại'],
            250000, 100000
        ),
        (
            ARRAY['quan_ao', 'xem_phim', 'the_thao', 'thuoc', 'sach_vo'],
            ARRAY['Mua sắm áo quần', 'Vé xem phim CGV', 'Tiền thuê sân bóng', 'Mua thuốc cảm', 'Mua sách chuyên ngành'],
            120000, 60000
        )
    ) AS v(cats, names, base_amount, step_amount)
    ORDER BY random()
    LIMIT 1
) cat
CROSS JOIN generate_series(1, 40) AS s;

-- Bật lại trigger cập nhật số dư ví
ALTER TABLE transactions ENABLE TRIGGER trg_transactions_wallet_balance;

-- =============================================================
-- 3. ĐỒNG BỘ SỐ DƯ TẤT CẢ CÁC VÍ THEO ĐÚNG LỊCH SỬ GIAO DỊCH
-- Đảm bảo chuẩn xác 100% theo công thức fn_txn_wallet_effect
-- =============================================================
UPDATE wallets w
SET balance = COALESCE((
    SELECT SUM(fn_txn_wallet_effect(t.type, t.amount))
    FROM transactions t
    WHERE t.wallet_id = w.id
      AND NOT t.is_deleted
), 0);
