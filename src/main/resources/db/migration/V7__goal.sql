-- =============================================================
-- V7 — Module Mục tiêu tiết kiệm (Savings Goal)
-- =============================================================

-- -------------------------------------------------------------
-- 1. savings_goals — Mục tiêu tiết kiệm
-- -------------------------------------------------------------
CREATE TABLE savings_goals (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    wallet_id     UUID,
    name          VARCHAR(100) NOT NULL,
    target_amount BIGINT       NOT NULL,
    saved_amount  BIGINT       NOT NULL DEFAULT 0,
    target_date   DATE,
    icon_id       UUID,
    status        VARCHAR(20)  NOT NULL DEFAULT 'in_progress',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_goal_user   FOREIGN KEY (user_id)   REFERENCES users   (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE SET NULL,
    CONSTRAINT fk_goal_icon   FOREIGN KEY (icon_id)   REFERENCES icons   (id) ON DELETE SET NULL,

    CONSTRAINT ck_goal_target CHECK (target_amount > 0),
    CONSTRAINT ck_goal_saved  CHECK (saved_amount >= 0),
    CONSTRAINT ck_goal_status CHECK (status IN ('in_progress', 'completed', 'cancelled'))
);

COMMENT ON COLUMN savings_goals.saved_amount IS 'Cộng dồn từ goal_contributions. Do trigger sở hữu';

CREATE INDEX idx_goal_user ON savings_goals (user_id, status);

-- -------------------------------------------------------------
-- 2. goal_contributions — Từng lần đóng góp / nạp vào mục tiêu
-- -------------------------------------------------------------
CREATE TABLE goal_contributions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id          UUID        NOT NULL,
    transaction_id   UUID,
    amount           BIGINT      NOT NULL,
    contributed_date DATE        NOT NULL DEFAULT CURRENT_DATE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_gc_goal   FOREIGN KEY (goal_id)        REFERENCES savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_gc_txn    FOREIGN KEY (transaction_id) REFERENCES transactions  (id) ON DELETE SET NULL,
    CONSTRAINT ck_gc_amount CHECK (amount > 0)
);

CREATE INDEX idx_gc_goal ON goal_contributions (goal_id, contributed_date);

-- -------------------------------------------------------------
-- Tự cộng dồn saved_amount và cập nhật trạng thái mục tiêu
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_goal_contributions_sync()
RETURNS TRIGGER AS $$
DECLARE
    v_goal_id UUID := COALESCE(NEW.goal_id, OLD.goal_id);
    v_total   BIGINT;
BEGIN
    SELECT COALESCE(SUM(amount), 0) INTO v_total
      FROM goal_contributions WHERE goal_id = v_goal_id;

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
