-- Bảng tổng hợp số dư từng thành viên trong nhóm, do tầng service cộng dồn chênh lệch mỗi lần ghi giao dịch.
-- Báo cáo số dư và kiểm hạn mức hoàn tiền đọc thẳng bảng này thay vì cộng lại toàn bộ lịch sử giao dịch.
CREATE TABLE group_member_balances (
    group_id           UUID   NOT NULL,
    user_id            UUID   NOT NULL,
    paid_out_of_pocket BIGINT NOT NULL DEFAULT 0,
    contribution       BIGINT NOT NULL DEFAULT 0,
    refund             BIGINT NOT NULL DEFAULT 0,
    share              BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_gmb_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gmb_user  FOREIGN KEY (user_id)  REFERENCES users  (id) ON DELETE CASCADE
);

COMMENT ON TABLE group_member_balances IS 'Số dư tích luỹ của từng thành viên, chỉ tính giao dịch CONFIRMED chưa xoá; service cập nhật bằng chênh lệch trước/sau mỗi lần ghi';
COMMENT ON COLUMN group_member_balances.paid_out_of_pocket IS 'Tổng EXPENSE nguồn PERSONAL do người này bỏ tiền túi';
COMMENT ON COLUMN group_member_balances.contribution IS 'Tổng CONTRIBUTION người này nộp quỹ';
COMMENT ON COLUMN group_member_balances.refund IS 'Tổng REFUND quỹ trả cho người này';
COMMENT ON COLUMN group_member_balances.share IS 'Tổng phần phải chịu theo group_transaction_participants; ADJUSTMENT_UP tính âm';

-- Tính lại toàn bộ bảng từ lịch sử giao dịch, dùng khi dữ liệu được chèn thẳng bằng SQL không qua service
-- (file seed, perf test). Dùng DELETE thay TRUNCATE để không khoá độc quyền cả bảng.
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

-- CSDL dựng mới thì bảng giao dịch đang trống, lệnh này chỉ có tác dụng với CSDL đã có dữ liệu từ trước
SELECT fn_rebuild_group_member_balances();
