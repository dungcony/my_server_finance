-- =============================================================
-- Dữ liệu mẫu (Seed Data) cho module User, Auth và Group
-- 100.000 User với UUID có quy luật cố định, 10.000 Nhóm thông thường,
-- 1 Nhóm Siêu Lớn (100 thành viên, 1.000.000 giao dịch, 3.500.000 người chia tiền)
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

-- Tăng work_mem tạm thời cho phiên thực thi để tăng tốc độ nạp dữ liệu lớn
SET work_mem = '64MB';

-- =============================================================
-- KHỐI DỮ LIỆU: 100.000 TÀI KHOẢN NGƯỜI DÙNG (USERS & ROLES)
-- UUID có quy luật cố định: 00000000-0000-0000-0000-XXXXXXXXXXXX
-- =============================================================

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

-- =============================================================
-- KHỐI DỮ LIỆU: NHÓM SIÊU LỚN (SUPER GROUP CHO STRESS TEST)
-- 100 thành viên (user1 đến user100), 1 quỹ nhóm, 1.000.000 giao dịch
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

-- =============================================================
-- KHỐI SINH DỮ LIỆU LỚN: 1.000.000 GIAO DỊCH & 3.500.000 DÒNG CHIA TIỀN
-- Dồn vào Nhóm Siêu Lớn để stress test hiệu năng tối đa
-- =============================================================

CREATE OR REPLACE PROCEDURE seed_bulk_group_transactions(
    p_total_count INT DEFAULT 1000000,
    p_batch_size INT DEFAULT 100000
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_inserted INT := 0;
    v_current_batch INT;
    v_target_group_id UUID := 'c0000000-0000-0000-0000-000000000001';
    v_category_ids UUID[];
    v_member_ids UUID[];
    v_start_time TIMESTAMPTZ := clock_timestamp();
BEGIN
    -- Lấy danh mục chi tiêu hợp lệ từ hệ thống
    SELECT array_agg(id) INTO v_category_ids
    FROM categories
    WHERE type = 'expense'
    LIMIT 20;

    IF v_category_ids IS NULL OR array_length(v_category_ids, 1) = 0 THEN
        RAISE EXCEPTION 'Chưa tìm thấy danh mục chi tiêu (type = expense) trong bảng categories. Vui lòng chạy migration V3 trước!';
    END IF;

    -- Lấy danh sách 100 thành viên của nhóm siêu lớn theo thứ tự cố định
    SELECT array_agg(user_id ORDER BY joined_at, id) INTO v_member_ids
    FROM group_members
    WHERE group_id = v_target_group_id;

    IF v_member_ids IS NULL OR array_length(v_member_ids, 1) < 100 THEN
        RAISE EXCEPTION 'Chưa tìm đủ 100 thành viên trong nhóm siêu lớn!';
    END IF;

    RAISE NOTICE 'Bắt đầu nạp % giao dịch nhóm vào Nhóm Siêu Lớn theo từng đợt % bản ghi...', p_total_count, p_batch_size;

    -- Vòng lặp nạp dữ liệu theo lô
    WHILE v_inserted < p_total_count LOOP
        v_current_batch := LEAST(p_batch_size, p_total_count - v_inserted);

        -- Nạp đồng thời giao dịch và người chia tiền (trung bình 3 đến 4 người chia)
        WITH generated_data AS (
            SELECT
                gen_random_uuid() AS tx_id,
                -- Chọn vị trí bắt đầu từ 1 đến 96 để đảm bảo lấy liên tiếp 4 người không bị tràn mảng
                (1 + (floor(random() * 95))::INT) AS start_idx,
                -- Số lượng người cùng chia tiền cho giao dịch này (3 hoặc 4 người)
                (3 + (floor(random() * 2))::INT) AS num_participants,
                -- Số tiền giao dịch (chia hết cho 12.000 để phân bổ chẵn)
                ((floor(random() * 80 + 5) * 12000))::BIGINT AS total_amount,
                v_category_ids[1 + (floor(random() * array_length(v_category_ids, 1)))::INT] AS cat_id,
                (ARRAY['FUND', 'PERSONAL'])[1 + (floor(random() * 2))::INT] AS src,
                now() - (random() * interval '360 days') AS tx_time,
                (v_inserted + s) AS tx_seq
            FROM generate_series(1, v_current_batch) AS s
        ),
        inserted_tx AS (
            INSERT INTO group_transactions (
                id,
                group_id,
                money_source,
                transactor_id,
                created_by,
                category_id,
                type,
                status,
                reviewed_by,
                reviewed_at,
                amount,
                occurred_at,
                note,
                version,
                created_at,
                updated_at
            )
            SELECT
                gd.tx_id,
                v_target_group_id,
                gd.src,
                v_member_ids[gd.start_idx],
                v_member_ids[gd.start_idx],
                gd.cat_id,
                'EXPENSE',
                'CONFIRMED',
                v_member_ids[gd.start_idx],
                gd.tx_time,
                gd.total_amount,
                gd.tx_time,
                'Chi tiêu siêu nhóm đợt ' || gd.tx_seq,
                0,
                gd.tx_time,
                gd.tx_time
            FROM generated_data gd
            RETURNING id
        )
        -- Chèn người cùng chia tiền (mỗi giao dịch có 3 đến 4 bản ghi tương ứng)
        INSERT INTO group_transaction_participants (
            group_transaction_id,
            user_id,
            share_amount
        )
        SELECT
            gd.tx_id,
            v_member_ids[gd.start_idx + p.offset_idx],
            (gd.total_amount / gd.num_participants)::BIGINT
        FROM generated_data gd
        CROSS JOIN LATERAL (
            SELECT generate_series(0, gd.num_participants - 1) AS offset_idx
        ) p;

        v_inserted := v_inserted + v_current_batch;
        RAISE NOTICE 'Tiến độ giao dịch: % / % (Hoàn thành % %%)',
            v_inserted, p_total_count, ROUND((v_inserted::NUMERIC / p_total_count::NUMERIC) * 100, 1);

        -- Lưu dữ liệu từng đợt để giải phóng bộ nhớ
        COMMIT;
    END LOOP;

    RAISE NOTICE 'Hoàn thành nạp % giao dịch và hơn 3.500.000 bản ghi chia tiền sau %!',
        p_total_count, clock_timestamp() - v_start_time;
END;
$$;

-- Thực thi nạp 1.000.000 giao dịch và hơn 3.500.000 dòng chia tiền
CALL seed_bulk_group_transactions(1000000, 100000);

-- =============================================================
-- TÍNH LẠI SỐ LIỆU PHÁI SINH TỪ LỊCH SỬ GIAO DỊCH
-- Giao dịch ở trên được chèn thẳng bằng SQL, không đi qua service, nên bảng tổng hợp
-- số dư thành viên và số dư quỹ phải được tính lại cho khớp lịch sử
-- =============================================================

-- Bảng tổng hợp số dư từng thành viên (function tạo ở migration V18)
SELECT fn_rebuild_group_member_balances();

-- Số dư quỹ theo đúng công thức TransactionHelper.calculateDelta, thay cho số gán cứng lúc tạo quỹ
UPDATE group_funds f
SET current_balance = COALESCE((
    SELECT SUM(CASE
        WHEN gt.type = 'CONTRIBUTION' AND gt.money_source = 'PERSONAL' THEN gt.amount
        WHEN gt.type = 'EXPENSE' AND gt.money_source = 'FUND' THEN -gt.amount
        WHEN gt.type IN ('REFUND', 'ADJUSTMENT_DOWN') THEN -gt.amount
        WHEN gt.type = 'ADJUSTMENT_UP' THEN gt.amount
        ELSE 0 END)
    FROM group_transactions gt
    WHERE gt.group_id = f.group_id
      AND gt.status = 'CONFIRMED'
      AND gt.deleted_at IS NULL), 0);
