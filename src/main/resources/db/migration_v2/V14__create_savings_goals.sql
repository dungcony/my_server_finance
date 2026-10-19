-- =============================================================
-- V14: KHỐI MỤC TIÊU TIẾT KIỆM (SAVINGS GOALS & CONTRIBUTIONS)
-- VÀ BỔ SUNG RÀNG BUỘC VÍ 'SAVINGS'
-- =============================================================

-- 1. Bổ sung ràng buộc kiểm tra loại ví trong wallets
ALTER TABLE wallets DROP CONSTRAINT IF EXISTS ck_wal_type;
ALTER TABLE wallets ADD CONSTRAINT ck_wal_type 
    CHECK (type IN ('CASH', 'BANK', 'E_WALLET', 'LOAN', 'SAVINGS'));

-- 2. Bảng savings_goals - Mục tiêu tích lũy tài chính
CREATE TABLE savings_goals (
    id            UUID         PRIMARY KEY,
    user_id       UUID         NOT NULL,
    wallet_id     UUID,
    name          VARCHAR(100) NOT NULL,
    target_amount BIGINT       NOT NULL,
    saved_amount  BIGINT       NOT NULL DEFAULT 0,
    target_date   DATE,
    icon_id       UUID,
    status        VARCHAR(20)  NOT NULL DEFAULT 'in_progress',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_goal_user   FOREIGN KEY (user_id)   REFERENCES users   (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE SET NULL,
    CONSTRAINT fk_goal_icon   FOREIGN KEY (icon_id)   REFERENCES icons   (id) ON DELETE SET NULL,
    CONSTRAINT ck_goal_target CHECK (target_amount > 0),
    CONSTRAINT ck_goal_saved  CHECK (saved_amount >= 0),
    CONSTRAINT ck_goal_status CHECK (status IN ('in_progress', 'completed', 'cancelled'))
);

CREATE INDEX idx_goals_user_status ON savings_goals (user_id, status) WHERE NOT is_deleted;

-- 3. Bảng goal_contributions - Lịch sử các lần đóng góp vào mục tiêu
CREATE TABLE goal_contributions (
    id                    UUID        PRIMARY KEY,
    goal_id               UUID        NOT NULL,
    transaction_id        UUID,        -- Giao dịch trừ tiền ở ví nguồn
    target_transaction_id UUID,        -- Giao dịch cộng tiền ở ví tiết kiệm
    amount                BIGINT      NOT NULL,
    contributed_date      DATE        NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_deleted            BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_gc_goal       FOREIGN KEY (goal_id)               REFERENCES savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_gc_txn        FOREIGN KEY (transaction_id)        REFERENCES transactions  (id) ON DELETE SET NULL,
    CONSTRAINT fk_gc_target_txn FOREIGN KEY (target_transaction_id) REFERENCES transactions  (id) ON DELETE SET NULL,
    CONSTRAINT ck_gc_amount     CHECK (amount > 0)
);

CREATE INDEX idx_gc_goal_date ON goal_contributions (goal_id, contributed_date) WHERE NOT is_deleted;

-- 4. Triggers cập nhật updated_at
CREATE TRIGGER trg_savings_goals_updated_at
    BEFORE UPDATE ON savings_goals
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

CREATE TRIGGER trg_goal_contributions_updated_at
    BEFORE UPDATE ON goal_contributions
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- 5. Trigger tự động đồng bộ saved_amount và chuyển đổi trạng thái mục tiêu
CREATE OR REPLACE FUNCTION fn_goal_contributions_sync()
RETURNS TRIGGER AS $$
DECLARE
    v_goal_id UUID := COALESCE(NEW.goal_id, OLD.goal_id);
    v_total   BIGINT;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_total
      FROM goal_contributions WHERE goal_id = v_goal_id AND NOT is_deleted;

    UPDATE savings_goals
       SET saved_amount = v_total,
           status = CASE
               WHEN status = 'cancelled'        THEN 'cancelled'
               WHEN v_total >= target_amount    THEN 'completed'
               ELSE 'in_progress'
           END
     WHERE id = v_goal_id;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_goal_contributions_sync
    AFTER INSERT OR UPDATE OR DELETE ON goal_contributions
    FOR EACH ROW EXECUTE FUNCTION fn_goal_contributions_sync();

-- 6. Danh mục hệ thống cho nạp/rút tiết kiệm
INSERT INTO categories (code, name, icon_code, color, level, path)
SELECT 'tiet_kiem', 'Tiết kiệm', 'vi_tien', '#2f8272', 0, 'tiet_kiem'
FROM icons i WHERE i.code = 'vi_tien'
ON CONFLICT (code) DO NOTHING;
