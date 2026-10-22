-- chặn xoá mềm giao dịch đang gắn với khoản nợ (giao dịch gốc hoặc một lần trả nợ)
CREATE OR REPLACE FUNCTION fn_transactions_block_debt_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM debt_payments WHERE transaction_id = NEW.id)
       OR EXISTS (SELECT 1 FROM debts WHERE origin_transaction_id = NEW.id) THEN
        RAISE EXCEPTION 'TRANSACTION_LINKED_TO_DEBT' USING ERRCODE = 'P0001';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_block_debt_delete
    BEFORE UPDATE OF is_deleted ON transactions
    FOR EACH ROW
    WHEN (NEW.is_deleted AND NOT OLD.is_deleted)
    EXECUTE FUNCTION fn_transactions_block_debt_delete();