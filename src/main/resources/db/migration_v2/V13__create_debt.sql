-- =============================================================
-- KHỐI CÔNG NỢ (SỔ NỢ - DEBT & DEBT_PAYMENTS)
-- =============================================================

-- 1. Bảng debts: Sổ ghi nợ và cho vay cá nhân
CREATE TABLE debts (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL,
    wallet_id     UUID         NOT NULL,
    type          VARCHAR(10)  NOT NULL, -- 'LENDING' (cho vay) hoặc 'BORROWING' (đi vay)
    name          VARCHAR(100) NOT NULL,
    total_amount  BIGINT       NOT NULL, -- Số tiền cho/đi vay
    paid_amount   BIGINT       NOT NULL DEFAULT 0, -- Số tiền đã trả (do trigger quản lý)
    issued_date   DATE         NOT NULL, -- Ngày tạo nợ
    due_date      DATE,                  -- Hạn trả nợ
    note          TEXT,                  -- Ghi chú
    status        VARCHAR(20)  NOT NULL DEFAULT 'INPROCESS',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_debt_user      FOREIGN KEY (user_id)   REFERENCES users   (id) ON DELETE CASCADE,
    CONSTRAINT fk_debt_wallet    FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE RESTRICT,
    CONSTRAINT ck_debt_type      CHECK (type IN ('LENDING', 'BORROWING')),
    CONSTRAINT ck_debt_total     CHECK (total_amount > 0),
    CONSTRAINT ck_debt_paid      CHECK (paid_amount >= 0),
    CONSTRAINT ck_debt_status    CHECK (status IN ('INPROCESS', 'COMPLETED')),
    CONSTRAINT ck_debt_due_date  CHECK (due_date IS NULL OR due_date >= issued_date),
    CONSTRAINT ck_debt_paid_limit CHECK (paid_amount <= total_amount)
);

CREATE INDEX idx_debts_user_status ON debts (user_id, status) WHERE NOT is_deleted;
CREATE INDEX idx_debts_due_date ON debts (due_date) WHERE status = 'INPROCESS' AND NOT is_deleted;
CREATE INDEX idx_debts_wallet_id ON debts (wallet_id) WHERE NOT is_deleted;

-- 2. Bảng debt_payments: Lịch sử từng lần trả nợ (Giao dịch độc lập)
CREATE TABLE debt_payments (
    id             UUID        PRIMARY KEY,
    debt_id        UUID        NOT NULL,
    amount         BIGINT      NOT NULL,
    paid_date      DATE        NOT NULL,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_dp_debt   FOREIGN KEY (debt_id) REFERENCES debts (id) ON DELETE CASCADE,
    CONSTRAINT ck_dp_amount CHECK (amount > 0)
);

CREATE INDEX idx_debt_payments_debt_id ON debt_payments (debt_id) WHERE NOT is_deleted;

-- 3. Trigger cập nhật updated_at
CREATE TRIGGER trg_debts_updated_at
    BEFORE UPDATE ON debts
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_debt_payments_updated_at
    BEFORE UPDATE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- 4. Trigger đồng bộ paid_amount và chuyển đổi trạng thái settled/outstanding
CREATE OR REPLACE FUNCTION fn_debt_payments_sync()
RETURNS TRIGGER AS $$
DECLARE
    v_debt_id UUID := COALESCE(NEW.debt_id, OLD.debt_id);
    v_total   BIGINT;
    v_debt    debts%ROWTYPE;
BEGIN
    SELECT * INTO v_debt FROM debts WHERE id = v_debt_id FOR UPDATE;

    SELECT COALESCE(SUM(amount), 0) INTO v_total
      FROM debt_payments WHERE debt_id = v_debt_id AND NOT is_deleted;

    IF v_total > v_debt.total_amount THEN
        RAISE EXCEPTION 'TỔNG ĐÃ TRẢ VƯỢT QUÁ SỐ NỢ GỐC' USING ERRCODE = 'P0001';
    END IF;

    UPDATE debts
       SET paid_amount = v_total,
           status = CASE
               WHEN v_total >= total_amount THEN 'COMPLETED'
               ELSE 'INPROCESS'
           END,
           updated_at = now()
     WHERE id = v_debt_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_debt_payments_sync
    AFTER INSERT OR UPDATE OR DELETE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION fn_debt_payments_sync();

-- 5. Trigger tự động cập nhật số dư ví theo net effect (-total + paid hoặc +total - paid)
CREATE OR REPLACE FUNCTION fn_debt_wallet_net_effect(p_type VARCHAR, p_total BIGINT, p_paid BIGINT)
RETURNS BIGINT AS $$
    SELECT CASE
        WHEN p_type = 'LENDING'   THEN -p_total + p_paid
        WHEN p_type = 'BORROWING' THEN  p_total - p_paid
        ELSE 0
    END;
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION fn_debts_wallet_balance()
RETURNS TRIGGER AS $$
DECLARE
    v_old_effect BIGINT;
    v_new_effect BIGINT;
    v_diff       BIGINT;
BEGIN
    -- Tính net effect của trạng thái cũ (0 nếu đã xóa mềm)
    v_old_effect := CASE
        WHEN TG_OP IN ('UPDATE', 'DELETE') AND NOT OLD.is_deleted
            THEN fn_debt_wallet_net_effect(OLD.type, OLD.total_amount, OLD.paid_amount)
        ELSE 0
    END;

    -- Tính net effect của trạng thái mới (0 nếu đã xóa mềm)
    v_new_effect := CASE
        WHEN TG_OP IN ('INSERT', 'UPDATE') AND NOT NEW.is_deleted
            THEN fn_debt_wallet_net_effect(NEW.type, NEW.total_amount, NEW.paid_amount)
        ELSE 0
    END;

    -- Trường hợp UPDATE cùng ví: chỉ cần 1 câu UPDATE với delta
    IF TG_OP = 'UPDATE' AND OLD.wallet_id = NEW.wallet_id THEN
        v_diff := v_new_effect - v_old_effect;
        IF v_diff <> 0 THEN
            UPDATE wallets SET balance = balance + v_diff WHERE id = NEW.wallet_id;
        END IF;

    -- Trường hợp đổi ví hoặc INSERT/DELETE: hoàn tác ví cũ rồi áp vào ví mới
    ELSE
        IF v_old_effect <> 0 THEN
            UPDATE wallets SET balance = balance - v_old_effect WHERE id = OLD.wallet_id;
        END IF;
        IF v_new_effect <> 0 THEN
            UPDATE wallets SET balance = balance + v_new_effect WHERE id = NEW.wallet_id;
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_debts_wallet_balance
    AFTER INSERT OR DELETE OR UPDATE OF type, total_amount, paid_amount, wallet_id, is_deleted ON debts
    FOR EACH ROW
    EXECUTE FUNCTION fn_debts_wallet_balance();

-- 6. Trigger chặn xóa khoản nợ khi đang INPROCESS
CREATE OR REPLACE FUNCTION fn_debts_block_delete_inprocess()
RETURNS TRIGGER AS $$
BEGIN
  IF (TG_OP = 'UPDATE' AND NEW.is_deleted AND NOT OLD.is_deleted) OR TG_OP = 'DELETE' THEN
        -- Chỉ chặn nếu nợ đang INPROCESS và ĐÃ TRẢ một phần tiền
        IF UPPER(OLD.status) IN ('INPROCESS', 'IN_PROCESS') AND OLD.paid_amount > 0 THEN
            RAISE EXCEPTION 'DEBT_CANNOT_DELETE_WHILE_IN_PROCESS'
                USING ERRCODE = 'P0001',
                MESSAGE = 'Không thể xóa khoản nợ đang trong quá trình thực hiện (in_process).';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_debts_block_delete_inprocess
    BEFORE UPDATE OF is_deleted OR DELETE ON debts
    FOR EACH ROW
    EXECUTE FUNCTION fn_debts_block_delete_inprocess();
