-- Gộp paid_out_of_pocket vào contribution trong group_member_balances.
-- Cả hai đều là tiền thành viên ứng cho nhóm và được cộng ngang hàng khi tính số dư ròng,
-- nên chỉ cần giữ một cột.

UPDATE group_member_balances
SET contribution = contribution + paid_out_of_pocket;

ALTER TABLE group_member_balances
    DROP COLUMN paid_out_of_pocket;

COMMENT ON COLUMN group_member_balances.contribution IS
    'Tổng tiền thành viên đã ứng cho nhóm: nộp quỹ (CONTRIBUTION) và chi hộ bằng tiền cá nhân (EXPENSE nguồn PERSONAL)';

-- Hàm tính lại bảng số dư: bỏ cột paid_out_of_pocket, gộp hai nguồn vào contribution
CREATE OR REPLACE FUNCTION fn_rebuild_group_member_balances() RETURNS void
    LANGUAGE plpgsql AS
$$
BEGIN
    DELETE FROM group_member_balances;

    INSERT INTO group_member_balances (group_id, user_id, contribution, refund, share)
    SELECT group_id,
           user_id,
           SUM(contribution),
           SUM(refund),
           SUM(share)
    FROM (
        SELECT group_id, transactor_id AS user_id, amount AS contribution, 0 AS refund, 0 AS share
        FROM group_transactions
        WHERE status = 'CONFIRMED'
          AND deleted_at IS NULL
          AND (type = 'CONTRIBUTION'
               OR (type = 'EXPENSE' AND money_source = 'PERSONAL'))

        UNION ALL

        SELECT gt.group_id, p.user_id, 0, p.share_amount, 0
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
