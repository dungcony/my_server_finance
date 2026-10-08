-- =============================================================
-- TÍNH LẠI SỐ LIỆU PHÁI SINH TỪ LỊCH SỬ GIAO DỊCH
-- Giao dịch được chèn thẳng bằng SQL, không đi qua service, nên bảng tổng hợp
-- số dư thành viên và số dư quỹ phải được tính lại cho khớp lịch sử
-- Yêu cầu: Đã chạy hoàn tất 04_execute_seed_transactions.sql
-- =============================================================

-- Tăng work_mem tạm thời để tăng tốc độ gom nhóm và tính toán
SET work_mem = '64MB';

-- Bảng tổng hợp số dư từng thành viên (function tạo ở migration V18)
SELECT fn_rebuild_group_member_balances();

-- Số dư quỹ theo đúng công thức TransactionHelper.calculateDelta, chỉ tính cho Nhóm Siêu Lớn
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
      AND gt.deleted_at IS NULL), 0)
WHERE f.group_id = 'c0000000-0000-0000-0000-000000000001';

