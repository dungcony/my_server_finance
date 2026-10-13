-- =============================================================
-- V3 — Hàm và Trigger nghiệp vụ
-- =============================================================

-- =============================================================
-- KHỐI GIAO DỊCH (TRANSACTION)
-- =============================================================

-- Cập nhật updated_at mỗi lần ghi giao dịch
CREATE OR REPLACE FUNCTION fn_transactions_validate()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_validate
    BEFORE INSERT OR UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION fn_transactions_validate();

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

        SELECT gt.group_id, p.user_id, 0, 0, p.share_amount, 0
        FROM group_transaction_participants p
        JOIN group_transactions gt ON gt.id = p.group_transaction_id
        WHERE gt.status = 'CONFIRMED'
          AND gt.deleted_at IS NULL
          AND gt.type = 'REFUND'

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
          AND gt.type IN ('EXPENSE', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN')
    ) combined
    GROUP BY group_id, user_id;
END;
$$;

SELECT fn_rebuild_group_member_balances();
