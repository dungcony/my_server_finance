-- =============================================================
-- KHỐI CÔNG NỢ (SỔ NỢ - DEBT & DEBT_PAYMENTS)
-- =============================================================

-- Bảng debts: Sổ ghi nợ và cho vay cá nhân
CREATE TABLE debts (
    id                    UUID         PRIMARY KEY,
    user_id               UUID         NOT NULL,
    wallet_id             UUID         NOT NULL,
    type                  VARCHAR(10)  NOT NULL, -- 'lending' (cho vay) hoặc 'borrowing' (đi vay)
    counterparty_name     VARCHAR(100) NOT NULL, -- Tên đối tác vay/cho vay
    principal_amount      BIGINT       NOT NULL, -- Số tiền gốc ban đầu
    paid_amount           BIGINT       NOT NULL DEFAULT 0, -- Số tiền đã trả (do trigger quản lý)
    issued_date           DATE         NOT NULL, -- Ngày tạo nợ
    due_date              DATE,                  -- Hạn trả nợ
    note                  TEXT,                  -- Ghi chú
    status                VARCHAR(20)  NOT NULL DEFAULT 'outstanding', -- 'outstanding', 'settled', 'written_off'
    origin_transaction_id UUID         NOT NULL, -- Giao dịch gốc lúc vay/cho vay
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_debt_user      FOREIGN KEY (user_id)               REFERENCES users        (id) ON DELETE CASCADE,
    CONSTRAINT fk_debt_wallet    FOREIGN KEY (wallet_id)             REFERENCES wallets      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debt_txn       FOREIGN KEY (origin_transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_debt_type      CHECK (type IN ('lending', 'borrowing')),
    CONSTRAINT ck_debt_principal CHECK (principal_amount > 0),
    CONSTRAINT ck_debt_paid      CHECK (paid_amount >= 0),
    CONSTRAINT ck_debt_status    CHECK (status IN ('outstanding', 'settled', 'written_off')),
    CONSTRAINT ck_debt_due_date  CHECK (due_date IS NULL OR due_date >= issued_date)
);

CREATE INDEX idx_debts_user_status ON debts (user_id, status) WHERE NOT is_deleted;
CREATE INDEX idx_debts_due_date ON debts (due_date) WHERE status = 'outstanding' AND NOT is_deleted;

-- Bảng debt_payments: Lịch sử từng lần trả nợ
CREATE TABLE debt_payments (
    id             UUID        PRIMARY KEY,
    debt_id        UUID        NOT NULL,
    transaction_id UUID        NOT NULL,
    amount         BIGINT      NOT NULL,
    paid_date      DATE        NOT NULL,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted     BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_dp_debt   FOREIGN KEY (debt_id)        REFERENCES debts        (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_txn    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_dp_amount CHECK (amount > 0)
);

CREATE INDEX idx_debt_payments_debt_id ON debt_payments (debt_id) WHERE NOT is_deleted;

-- Trigger tự động cập nhật updated_at
CREATE TRIGGER trg_debts_updated_at
    BEFORE UPDATE ON debts
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_debt_payments_updated_at
    BEFORE UPDATE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- Trigger tự động đồng bộ paid_amount và chuyển đổi trạng thái settled/outstanding
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

    IF v_total > v_debt.principal_amount THEN
        RAISE EXCEPTION 'TỔNG ĐÃ TRẢ VƯỢT QUÁ SỐ NỢ GỐC' USING ERRCODE = 'P0001';
    END IF;

    UPDATE debts
       SET paid_amount = v_total,
           status = CASE
               WHEN status = 'written_off'      THEN 'written_off'
               WHEN v_total >= principal_amount THEN 'settled'
               ELSE 'outstanding'
           END
     WHERE id = v_debt_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_debt_payments_sync
    AFTER INSERT OR UPDATE OR DELETE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION fn_debt_payments_sync();
