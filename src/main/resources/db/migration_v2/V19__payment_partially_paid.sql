-- =============================================================
-- Migration V19: Hỗ trợ thanh toán thiếu tiền (PARTIALLY_PAID)
-- và quá hạn bù tiền (FORFEITED) cho bảng payments
-- =============================================================

-- Cập nhật ràng buộc trạng thái thanh toán
ALTER TABLE payments DROP CONSTRAINT ck_payments_status;
ALTER TABLE payments ADD CONSTRAINT ck_payments_status
    CHECK (status IN ('PENDING', 'PARTIALLY_PAID', 'PAID', 'CANCELLED', 'EXPIRED', 'FORFEITED'));

-- Thêm các cột theo dõi thời hạn bù tiền và tịch thu tiền
ALTER TABLE payments
    ADD COLUMN topup_deadline TIMESTAMPTZ,
    ADD COLUMN topup_reminded_at TIMESTAMPTZ,
    ADD COLUMN forfeited_at TIMESTAMPTZ;

COMMENT ON COLUMN payments.topup_deadline IS 'Hạn chót bù tiền khi thanh toán thiếu; NULL khi đơn chưa từng thiếu tiền';
COMMENT ON COLUMN payments.topup_reminded_at IS 'Thời điểm đã gửi thông báo nhắc hạn bù tiền; NULL nếu chưa nhắc hoặc không cần nhắc';
COMMENT ON COLUMN payments.forfeited_at IS 'Thời điểm đơn bị đóng vĩnh viễn và giữ tiền do quá hạn bù; NULL nếu không bị phạt';

-- Cập nhật unique index: một user chỉ có tối đa một đơn đang mở (PENDING hoặc PARTIALLY_PAID) cho mỗi loại hàng hoá
DROP INDEX IF EXISTS uq_payments_pending_per_user_type;
CREATE UNIQUE INDEX uq_payments_active_per_user_type
    ON payments (user_id, type) WHERE status IN ('PENDING', 'PARTIALLY_PAID');

-- Index phục vụ job quét các đơn PARTIALLY_PAID theo hạn bù tiền
CREATE INDEX idx_payments_topup_deadline ON payments (topup_deadline) WHERE status = 'PARTIALLY_PAID';
