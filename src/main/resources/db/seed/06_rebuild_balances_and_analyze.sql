-- =============================================================
-- Dữ liệu mẫu (Seed Data) - Phần 6: Đồng bộ số dư & Tối ưu hóa thống kê DB
-- Tính toán lại toàn bộ số dư nhóm, số dư ví và cập nhật PostgreSQL Stats
-- Phù hợp với Schema migration_v2 (V4__group, V7__trigger_wallet_balance)
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

SET work_mem = '64MB';

-- =============================================================
-- 1. ĐỒNG BỘ SỐ DƯ TỪNG THÀNH VIÊN TRONG NHÓM
-- Gọi hàm fn_rebuild_group_member_balances() đã tạo ở V4__group
-- =============================================================
SELECT fn_rebuild_group_member_balances();

-- =============================================================
-- 2. ĐỒNG BỘ SỐ DƯ THỰC TẾ CỦA TẤT CẢ CÁC QUỸ NHÓM
-- Tính theo đúng chuẩn công thức logic hệ thống
-- =============================================================
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
      AND gt.deleted_at IS NULL
), 0);

-- =============================================================
-- 3. ĐỒNG BỘ SỐ DƯ TOÀN BỘ CÁC VÍ CÁ NHÂN
-- Đảm bảo cân bằng số dư ví theo fn_txn_wallet_effect
-- =============================================================
UPDATE wallets w
SET balance = COALESCE((
    SELECT SUM(fn_txn_wallet_effect(t.type, t.amount))
    FROM transactions t
    WHERE t.wallet_id = w.id
      AND NOT t.is_deleted
), 0);

-- Ghi nhật ký đối chiếu số dư mẫu vào reconcil_history
INSERT INTO reconcil_history (
    id,
    wallet_id,
    wallet_balance,
    total_transaction,
    is_deleted,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    w.id,
    w.balance,
    w.balance,
    false,
    now(),
    now()
FROM wallets w
LIMIT 20;

-- =============================================================
-- 4. VACUUM ANALYZE TỐI ƯU HÓA POSTGRESQL QUERY PLANNER
-- Giúp Postgres cập nhật chính xác số liệu thống kê index,
-- tối ưu hoá kế hoạch thực thi câu lệnh SQL (Explain Plan)
-- =============================================================
VACUUM ANALYZE users;
VACUUM ANALYZE user_roles;
VACUUM ANALYZE refresh_tokens;
VACUUM ANALYZE wallets;
VACUUM ANALYZE transactions;
VACUUM ANALYZE recurring_transactions;
VACUUM ANALYZE recurring_transaction_schedules;
VACUUM ANALYZE groups;
VACUUM ANALYZE group_members;
VACUUM ANALYZE group_funds;
VACUUM ANALYZE group_transactions;
VACUUM ANALYZE group_transaction_participants;
VACUUM ANALYZE group_member_balances;
