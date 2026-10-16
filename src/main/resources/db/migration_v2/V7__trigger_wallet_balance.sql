-- =============================================================
-- V6 — Trigger số dư ví: số dư luôn bằng tổng giao dịch chưa xoá của ví
-- Hai trigger trong file phụ thuộc nhau: sửa một cái phải xem lại cái kia, tránh cộng trùng.
-- =============================================================

-- -------------------------------------------------------------
-- Ảnh hưởng của một giao dịch lên số dư ví, cùng công thức với WalletRepository.reconcileAll
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_txn_wallet_effect(p_type VARCHAR, p_amount BIGINT)
RETURNS BIGINT AS $$
    SELECT CASE
        WHEN p_type = 'INCOME' THEN p_amount
        WHEN p_type IN ('EXPENSE', 'LOAN') THEN -p_amount
        ELSE 0
    END;
$$ LANGUAGE sql IMMUTABLE;

-- -------------------------------------------------------------
-- Giao dịch thay đổi thì cộng/trừ số dư ví: tạo, sửa số tiền/loại/ví, xoá mềm, khôi phục, xoá cứng.
-- Sửa cột khác (note, name...) thì trừ cũ cộng mới bằng 0, số dư giữ nguyên.
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_transactions_wallet_balance()
RETURNS TRIGGER AS $$
BEGIN
    -- gỡ ảnh hưởng của bản ghi cũ
    IF TG_OP IN ('UPDATE', 'DELETE') AND NOT OLD.is_deleted THEN
        UPDATE wallets
        SET balance = balance - fn_txn_wallet_effect(OLD.type, OLD.amount)
        WHERE id = OLD.wallet_id;
    END IF;

    -- cộng ảnh hưởng của bản ghi mới
    IF TG_OP IN ('INSERT', 'UPDATE') AND NOT NEW.is_deleted THEN
        UPDATE wallets
        SET balance = balance + fn_txn_wallet_effect(NEW.type, NEW.amount)
        WHERE id = NEW.wallet_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_wallet_balance
    AFTER INSERT OR DELETE OR UPDATE OF type, amount, wallet_id, is_deleted ON transactions
    FOR EACH ROW EXECUTE FUNCTION fn_transactions_wallet_balance();

-- -------------------------------------------------------------
-- Tạo ví có số dư > 0 thì sinh giao dịch thu "Số dư ban đầu".
-- Đưa số dư về 0 trước, để trg_transactions_wallet_balance cộng lại qua giao dịch này (không cộng trùng).
-- Không ghi category_id, source, is_deleted: để DB lấy giá trị mặc định.
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_wallets_initial_transaction()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.balance > 0 THEN
        UPDATE wallets SET balance = 0 WHERE id = NEW.id;

        INSERT INTO transactions (user_id, wallet_id, type, amount, occurred_at, name, note)
        VALUES (
            NEW.user_id,
            NEW.id,
            'INCOME',
            NEW.balance,
            COALESCE(NEW.created_at, now()),
            'Số dư ban đầu',
            'Tự động tạo từ số dư ban đầu khi mở ví'
        );
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wallets_initial_transaction
    AFTER INSERT ON wallets
    FOR EACH ROW EXECUTE FUNCTION fn_wallets_initial_transaction();
