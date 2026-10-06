-- Cập nhật dữ liệu cũ nếu tồn tại bản ghi share_amount null trước khi đặt NOT NULL
UPDATE group_transaction_participants p
SET share_amount = sub.calculated_share
FROM (
    SELECT 
        p2.group_transaction_id,
        p2.user_id,
        gt.amount / COUNT(*) OVER (PARTITION BY p2.group_transaction_id) AS calculated_share
    FROM group_transaction_participants p2
    JOIN group_transactions gt ON gt.id = p2.group_transaction_id
    WHERE p2.share_amount IS NULL
) sub
WHERE p.group_transaction_id = sub.group_transaction_id 
  AND p.user_id = sub.user_id 
  AND p.share_amount IS NULL;

-- Đặt ràng buộc NOT NULL cho cột share_amount
ALTER TABLE group_transaction_participants 
    ALTER COLUMN share_amount SET NOT NULL;

-- Cập nhật CHECK constraint bắt buộc share_amount > 0 và <= 999999999999
ALTER TABLE group_transaction_participants 
    DROP CONSTRAINT IF EXISTS ck_gtp_share_amount;

ALTER TABLE group_transaction_participants 
    ADD CONSTRAINT ck_gtp_share_amount CHECK (share_amount > 0 AND share_amount <= 999999999999);
