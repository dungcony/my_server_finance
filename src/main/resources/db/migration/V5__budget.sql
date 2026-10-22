-- =============================================================
-- V5 — Module Ngân sách & Tiến độ ngân sách (Budget)
-- =============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- -------------------------------------------------------------
-- 1. budgets — Hạn mức ngân sách thu chi
-- -------------------------------------------------------------
CREATE TABLE budgets (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID,
    group_id     UUID,
    category_id  UUID        NOT NULL,
    wallet_id    UUID,
    limit_amount BIGINT      NOT NULL,
    period_type  VARCHAR(10) NOT NULL,
    start_date   DATE        NOT NULL,
    end_date     DATE        NOT NULL,
    auto_renew   BOOLEAN     NOT NULL DEFAULT TRUE,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_bud_user     FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE CASCADE,
    CONSTRAINT fk_bud_group    FOREIGN KEY (group_id)    REFERENCES groups     (id) ON DELETE CASCADE,
    CONSTRAINT fk_bud_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bud_wallet   FOREIGN KEY (wallet_id)   REFERENCES wallets    (id) ON DELETE CASCADE,

    CONSTRAINT ck_bud_limit  CHECK (limit_amount > 0),
    CONSTRAINT ck_bud_period CHECK (period_type IN ('week', 'month', 'quarter', 'year')),
    CONSTRAINT ck_bud_dates  CHECK (end_date >= start_date),

    -- Ngân sách thuộc cá nhân HOẶC nhóm
    CONSTRAINT ck_bud_owner CHECK (
        (user_id IS NOT NULL AND group_id IS NULL) OR
        (user_id IS NULL     AND group_id IS NOT NULL)
    ),

    -- Chống ngân sách trùng chéo thời gian
    CONSTRAINT ex_bud_no_overlap EXCLUDE USING gist (
        COALESCE(user_id,  '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        COALESCE(group_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        category_id WITH =,
        COALESCE(wallet_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (is_active)
);

CREATE INDEX idx_bud_user   ON budgets (user_id, start_date, end_date) WHERE is_active;
CREATE INDEX idx_bud_group  ON budgets (group_id) WHERE group_id IS NOT NULL AND is_active;
CREATE INDEX idx_bud_renew  ON budgets (end_date) WHERE auto_renew AND is_active;

-- -------------------------------------------------------------
-- Ngân sách chỉ đặt cho danh mục CHI
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_budgets_validate()
RETURNS TRIGGER AS $$
DECLARE
    v_type VARCHAR(10);
BEGIN
    SELECT type INTO v_type FROM categories WHERE id = NEW.category_id;

    IF v_type <> 'expense' THEN
        RAISE EXCEPTION 'Chỉ đặt ngân sách cho danh mục chi (CATEGORY_NOT_EXPENSE)'
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_budgets_validate
    BEFORE INSERT OR UPDATE OF category_id ON budgets
    FOR EACH ROW EXECUTE FUNCTION fn_budgets_validate();

-- -------------------------------------------------------------
-- 2. v_budget_progress — View tính toán tiến độ ngân sách động
-- -------------------------------------------------------------
CREATE OR REPLACE VIEW v_budget_progress AS
SELECT
    b.id,
    b.user_id,
    b.group_id,
    b.category_id,
    b.wallet_id,
    b.limit_amount,
    b.period_type,
    b.start_date,
    b.end_date,
    b.auto_renew,
    b.is_active,
    COALESCE(s.spent_amount, 0)                        AS spent_amount,
    b.limit_amount - COALESCE(s.spent_amount, 0)       AS remaining,
    ROUND(COALESCE(s.spent_amount, 0)::numeric
          / NULLIF(b.limit_amount, 0), 4)              AS ratio,
    CASE
        WHEN COALESCE(s.spent_amount, 0) >= b.limit_amount            THEN 'over_limit'
        WHEN COALESCE(s.spent_amount, 0) >= b.limit_amount * 0.8      THEN 'near_limit'
        ELSE 'normal'
    END                                                AS status,
    GREATEST(b.end_date - CURRENT_DATE, 0)             AS days_remaining
FROM budgets b
LEFT JOIN LATERAL (
    SELECT SUM(t.amount) AS spent_amount
    FROM transactions t
    WHERE t.type = 'expense'
      AND NOT t.is_deleted
      AND t.counts_in_report
      AND t.date BETWEEN b.start_date AND b.end_date
      AND t.category_id IN (SELECT * FROM fn_category_tree(b.category_id))
      AND (b.wallet_id IS NULL OR t.wallet_id = b.wallet_id)
      AND (
            (b.user_id  IS NOT NULL AND t.user_id = b.user_id)
         OR (b.group_id IS NOT NULL AND t.wallet_id IN (
                SELECT w.id FROM wallets w
                 WHERE w.group_id = b.group_id AND NOT w.is_deleted
            ))
      )
) s ON TRUE;

COMMENT ON VIEW v_budget_progress IS
    'Ngân sách kèm số đã chi, tỉ lệ và trạng thái. Đã cộng gộp danh mục con, lọc counts_in_report và giới hạn đúng phạm vi cá nhân/nhóm.';
