-- =============================================================
-- V7 — Trigger xoá ví: xoá mềm ví thì xoá mềm giao dịch, tắt giao dịch định kỳ và bỏ các buổi lịch chưa chạy của ví
-- =============================================================

-- -------------------------------------------------------------
-- Chỉ chạy khi is_deleted đổi từ false sang true (WHEN bên dưới).
-- Hibernate ghi lại mọi cột mỗi lần save(wallet) nên UPDATE OF is_deleted chạy cả khi đổi tên ví,
-- điều kiện WHEN chặn các lần đó.
-- Mỗi giao dịch bị xoá mềm sẽ kích hoạt trg_transactions_wallet_balance (V6), số dư ví tự về 0.
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_wallets_soft_delete_cascade()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE transactions
    SET is_deleted = TRUE
    WHERE wallet_id = NEW.id
      AND NOT is_deleted;

    -- tắt giao dịch định kỳ của ví để scheduler không sinh giao dịch vào ví đã xoá
    UPDATE recurring_transactions
    SET is_enabled = FALSE
    WHERE wallet_id = NEW.id
      AND is_enabled;

    -- bỏ qua các buổi lịch chưa chạy, nếu để PENDING scheduler sẽ quét lại mỗi giờ
    UPDATE recurring_transaction_schedules s
    SET status = 'SKIPPED'
    FROM recurring_transactions r
    WHERE s.recurring_id = r.id
      AND r.wallet_id = NEW.id
      AND s.status = 'PENDING';

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallets_soft_delete_cascade
    AFTER UPDATE OF is_deleted ON wallets
    FOR EACH ROW
    WHEN (NEW.is_deleted AND NOT OLD.is_deleted)
    EXECUTE FUNCTION fn_wallets_soft_delete_cascade();
