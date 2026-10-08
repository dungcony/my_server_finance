-- =============================================================
-- Thủ tục lưu trữ (Stored Procedure): Sinh dữ liệu giao dịch nhóm cực lớn
-- Dồn 1.000.000 giao dịch và hơn 3.500.000 dòng chia tiền vào Nhóm Siêu Lớn
-- Cơ chế: Chia lô (batch) và COMMIT từng đợt để giải phóng bộ nhớ
-- Yêu cầu: Đã chạy 01_seed_users_and_auth.sql và 02_seed_groups_and_members.sql trước
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
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
