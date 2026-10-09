-- =============================================================
-- V3 — Hàm, Trigger kiểm tra nghiệp vụ và View báo cáo
-- =============================================================

-- =============================================================
-- KHỐI DANH MỤC (CATEGORY)
-- =============================================================

-- Kiểm tra ràng buộc cây danh mục tối đa hai cấp và cùng loại thu/chi
CREATE OR REPLACE FUNCTION fn_categories_validate()
RETURNS TRIGGER AS $$
DECLARE
    v_parent categories%ROWTYPE;
BEGIN
    IF NEW.parent_category_id IS NULL THEN
        RETURN NEW;
    END IF;

    IF NEW.parent_category_id = NEW.id THEN
        RAISE EXCEPTION 'Danh mục không thể là cha của chính nó'
            USING ERRCODE = 'check_violation';
    END IF;

    SELECT * INTO v_parent FROM categories WHERE id = NEW.parent_category_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Danh mục cha không tồn tại'
            USING ERRCODE = 'foreign_key_violation';
    END IF;

    IF v_parent.parent_category_id IS NOT NULL THEN
        RAISE EXCEPTION 'Danh mục chỉ được tối đa hai cấp (MAX_DEPTH_EXCEEDED)'
            USING ERRCODE = 'check_violation';
    END IF;

    IF v_parent.type <> NEW.type THEN
        RAISE EXCEPTION 'Danh mục con phải cùng loại với cha (TYPE_MISMATCH_WITH_PARENT)'
            USING ERRCODE = 'check_violation';
    END IF;

    NEW.category_group_id := v_parent.category_group_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_categories_validate
    BEFORE INSERT OR UPDATE OF parent_category_id, type ON categories
    FOR EACH ROW EXECUTE FUNCTION fn_categories_validate();

-- Chặn chuyển danh mục cha đang có con thành cấp con
CREATE OR REPLACE FUNCTION fn_categories_block_demote()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.parent_category_id IS NOT NULL
       AND OLD.parent_category_id IS NULL
       AND EXISTS (SELECT 1 FROM categories
                   WHERE parent_category_id = NEW.id AND NOT is_deleted) THEN
        RAISE EXCEPTION 'Danh mục đang có con, không thể chuyển thành cấp con (CATEGORY_HAS_CHILDREN)'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_categories_block_demote
    BEFORE UPDATE OF parent_category_id ON categories
    FOR EACH ROW EXECUTE FUNCTION fn_categories_block_demote();

-- Hàm lấy cây danh mục cha kèm các con (đã lọc xoá mềm)
CREATE OR REPLACE FUNCTION fn_category_tree(p_category_id UUID)
RETURNS TABLE (category_id UUID) AS $$
    SELECT id FROM categories WHERE id = p_category_id AND NOT is_deleted
    UNION
    SELECT id FROM categories WHERE parent_category_id = p_category_id AND NOT is_deleted;
$$ LANGUAGE sql STABLE;

COMMENT ON FUNCTION fn_category_tree IS
    'Trả về danh mục cha kèm mọi con còn hoạt động. Dùng cho lọc giao dịch, tính ngân sách và báo cáo.';

-- =============================================================
-- KHỐI GIAO DỊCH (TRANSACTION)
-- =============================================================

-- Kiểm tra loại danh mục khớp loại giao dịch và cập nhật updated_at
CREATE OR REPLACE FUNCTION fn_transactions_validate()
RETURNS TRIGGER AS $$
DECLARE
    v_cat_type VARCHAR(10);
BEGIN
    IF NEW.category_id IS NOT NULL THEN
        SELECT type INTO v_cat_type FROM categories WHERE id = NEW.category_id;
        IF v_cat_type IS DISTINCT FROM NEW.type THEN
            RAISE EXCEPTION 'Loại danh mục không khớp loại giao dịch (CATEGORY_TYPE_MISMATCH)'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_validate
    BEFORE INSERT OR UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION fn_transactions_validate();

-- =============================================================
-- KHỐI NGÂN SÁCH (BUDGET)
-- =============================================================

-- Kiểm tra ngân sách chỉ được tạo cho danh mục chi (expense)
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

-- View tính toán tiến độ ngân sách động
CREATE OR REPLACE VIEW v_budget_progress AS
SELECT
    b.id,
    b.user_id,
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
      AND t.user_id = b.user_id
) s ON TRUE;

COMMENT ON VIEW v_budget_progress IS
    'Ngân sách kèm số đã chi, tỉ lệ và trạng thái. Đã cộng gộp danh mục con, lọc counts_in_report và giới hạn đúng phạm vi cá nhân.';

-- =============================================================
-- KHỐI CÔNG NỢ (DEBT)
-- =============================================================

-- Tự cộng dồn paid_amount và cập nhật trạng thái khoản nợ khi trả nợ
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

-- =============================================================
-- KHỐI MỤC TIÊU TIẾT KIỆM (SAVINGS GOAL)
-- =============================================================

-- Tự cộng dồn saved_amount và cập nhật trạng thái mục tiêu tiết kiệm
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

-- =============================================================
-- KHỐI SỐ DƯ THÀNH VIÊN NHÓM (GROUP MEMBER BALANCES)
-- =============================================================

-- Tính lại toàn bộ bảng group_member_balances từ lịch sử giao dịch nhóm
CREATE OR REPLACE FUNCTION fn_rebuild_group_member_balances() RETURNS void
    LANGUAGE plpgsql AS
$$
BEGIN
    DELETE FROM group_member_balances;

    INSERT INTO group_member_balances (group_id, user_id, paid_out_of_pocket, contribution, refund, share)
    SELECT group_id,
           user_id,
           SUM(paid_out_of_pocket),
           SUM(contribution),
           SUM(refund),
           SUM(share)
    FROM (
        SELECT group_id, transactor_id AS user_id, amount AS paid_out_of_pocket, 0 AS contribution, 0 AS refund, 0 AS share
        FROM group_transactions
        WHERE status = 'CONFIRMED'
          AND deleted_at IS NULL
          AND type = 'EXPENSE'
          AND money_source = 'PERSONAL'

        UNION ALL

        SELECT group_id, transactor_id, 0, amount, 0, 0
        FROM group_transactions
        WHERE status = 'CONFIRMED'
          AND deleted_at IS NULL
          AND type = 'CONTRIBUTION'

        UNION ALL

        SELECT group_id, transactor_id, 0, 0, amount, 0
        FROM group_transactions
        WHERE status = 'CONFIRMED'
          AND deleted_at IS NULL
          AND type = 'REFUND'

        UNION ALL

        SELECT gt.group_id,
               p.user_id,
               0,
               0,
               0,
               CASE WHEN gt.type = 'ADJUSTMENT_UP' THEN -p.share_amount ELSE p.share_amount END
        FROM group_transaction_participants p
        JOIN group_transactions gt ON gt.id = p.group_transaction_id
        WHERE gt.status = 'CONFIRMED'
          AND gt.deleted_at IS NULL
    ) combined
    GROUP BY group_id, user_id;
END;
$$;

SELECT fn_rebuild_group_member_balances();
