-- =============================================================
-- V6 — Module Sổ nợ & Thanh toán nợ (Debt)
-- =============================================================

-- -------------------------------------------------------------
-- 1. debts — Bảng sổ nợ (Cho vay / Đi vay)
-- -------------------------------------------------------------
CREATE TABLE debts (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL,
    wallet_id             UUID         NOT NULL,
    type                  VARCHAR(10)  NOT NULL,
    counterparty_name     VARCHAR(100) NOT NULL,
    principal_amount      BIGINT       NOT NULL,
    paid_amount           BIGINT       NOT NULL DEFAULT 0,
    issued_date           DATE         NOT NULL DEFAULT CURRENT_DATE,
    due_date              DATE,
    note                  TEXT,
    status                VARCHAR(20)  NOT NULL DEFAULT 'outstanding',
    origin_transaction_id UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_debt_user   FOREIGN KEY (user_id)               REFERENCES users        (id) ON DELETE CASCADE,
    CONSTRAINT fk_debt_wallet FOREIGN KEY (wallet_id)             REFERENCES wallets      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debt_origin FOREIGN KEY (origin_transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,

    CONSTRAINT ck_debt_type      CHECK (type   IN ('lending', 'borrowing')),
    CONSTRAINT ck_debt_status    CHECK (status IN ('outstanding', 'settled', 'written_off')),
    CONSTRAINT ck_debt_principal CHECK (principal_amount > 0),
    CONSTRAINT ck_debt_paid      CHECK (paid_amount >= 0 AND paid_amount <= principal_amount),
    CONSTRAINT ck_debt_due       CHECK (due_date IS NULL OR due_date >= issued_date)
);

COMMENT ON COLUMN debts.type        IS 'lending = người khác nợ tôi, borrowing = tôi nợ người khác';
COMMENT ON COLUMN debts.paid_amount IS 'Cộng dồn từ debt_payments. Do trigger sở hữu';
COMMENT ON COLUMN debts.status      IS 'outstanding, settled, written_off';

CREATE INDEX idx_debt_user ON debts (user_id, status);
CREATE INDEX idx_debt_due  ON debts (due_date) WHERE status = 'outstanding' AND due_date IS NOT NULL;

-- -------------------------------------------------------------
-- 2. debt_payments — Từng lần trả nợ
-- -------------------------------------------------------------
CREATE TABLE debt_payments (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    debt_id        UUID        NOT NULL,
    transaction_id UUID        NOT NULL,
    amount         BIGINT      NOT NULL,
    paid_date      DATE        NOT NULL DEFAULT CURRENT_DATE,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_dp_debt   FOREIGN KEY (debt_id)        REFERENCES debts        (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_txn    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_dp_amount CHECK (amount > 0),
    CONSTRAINT uq_dp_txn    UNIQUE (transaction_id)
);

CREATE INDEX idx_dp_debt ON debt_payments (debt_id, paid_date);

-- -------------------------------------------------------------
-- Tự cộng dồn paid_amount và cập nhật trạng thái khoản nợ
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_debt_payments_sync()
RETURNS TRIGGER AS $$
DECLARE
    v_debt_id UUID := COALESCE(NEW.debt_id, OLD.debt_id);
    v_total   BIGINT;
    v_debt    debts%ROWTYPE;
BEGIN
    SELECT * INTO v_debt FROM debts WHERE id = v_debt_id FOR UPDATE;

    SELECT COALESCE(SUM(amount), 0) INTO v_total
      FROM debt_payments WHERE debt_id = v_debt_id;

    IF v_total > v_debt.principal_amount THEN
        RAISE EXCEPTION 'Tổng đã trả vượt số nợ gốc (EXCEEDS_REMAINING_AMOUNT)'
            USING ERRCODE = 'check_violation';
    END IF;

    UPDATE debts
       SET paid_amount = v_total,
           status = CASE
               WHEN status = 'written_off'              THEN 'written_off'
               WHEN v_total >= principal_amount         THEN 'settled'
               ELSE 'outstanding'
           END
     WHERE id = v_debt_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_debt_payments_sync
    AFTER INSERT OR UPDATE OR DELETE ON debt_payments
    FOR EACH ROW EXECUTE FUNCTION fn_debt_payments_sync();
