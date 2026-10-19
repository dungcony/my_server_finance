-- Thêm cột amount_paid vào bảng payments để lưu số tiền thực tế payOS đã thu
ALTER TABLE payments
    ADD COLUMN amount_paid BIGINT CHECK (amount_paid IS NULL OR amount_paid >= 0);

-- Backfill cho các đơn đã thanh toán thành công trước đó (nếu có)
UPDATE payments
SET amount_paid = amount
WHERE status = 'PAID' AND amount_paid IS NULL;
