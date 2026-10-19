-- =============================================================
-- Bảng payments: ghi nhận thanh toán payOS dùng chung cho mọi loại hàng hoá (type + reference_id)
-- và cột users.plan_expires_at: hạn dùng gói premium
-- =============================================================

CREATE SEQUENCE payments_order_code_seq START 100000;

CREATE TABLE payments (
    id            UUID         PRIMARY KEY,
    order_code    BIGINT       NOT NULL,
    user_id       UUID         NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    reference_id  VARCHAR(100),
    amount        BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payos_link_id VARCHAR(100),
    expires_at    TIMESTAMPTZ  NOT NULL,
    paid_at       TIMESTAMPTZ,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_payments_order_code UNIQUE (order_code),
    CONSTRAINT fk_payments_user       FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_payments_amount     CHECK (amount > 0),
    CONSTRAINT ck_payments_type       CHECK (type IN ('PREMIUM_UPGRADE')),
    CONSTRAINT ck_payments_status     CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED'))
);

COMMENT ON TABLE payments IS 'Giao dịch thanh toán payOS, dùng chung cho nhiều loại hàng hoá';
COMMENT ON COLUMN payments.order_code    IS 'Mã đơn gửi payOS, lấy từ payments_order_code_seq';
COMMENT ON COLUMN payments.type          IS 'Loại hàng hoá, tên enum PaymentType';
COMMENT ON COLUMN payments.reference_id  IS 'Thứ được mua (VD: mã gói PREMIUM_1M), do module sở hữu loại hàng hoá diễn giải';
COMMENT ON COLUMN payments.amount        IS 'Số tiền VND, số nguyên, do server tính';
COMMENT ON COLUMN payments.expires_at    IS 'Hạn thanh toán, cũng là expiredAt gửi cho payOS';

-- mỗi user chỉ có một đơn PENDING cho mỗi loại hàng hoá
CREATE UNIQUE INDEX uq_payments_pending_per_user_type
    ON payments (user_id, type) WHERE status = 'PENDING';
CREATE INDEX idx_payments_user    ON payments (user_id, created_at DESC);
CREATE INDEX idx_payments_overdue ON payments (expires_at) WHERE status = 'PENDING';

-- V10 chỉ gắn trigger updated_at cho bảng có lúc đó nên bảng mới phải tự gắn
CREATE TRIGGER trg_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

ALTER TABLE users ADD COLUMN plan_expires_at TIMESTAMPTZ;
COMMENT ON COLUMN users.plan_expires_at IS 'Hạn gói premium; NULL = không có hạn (VD: tài khoản admin seed)';

-- quyền xem lịch sử thanh toán toàn hệ thống, lưu theo tên enum PermissionName (PAYMENT_READ), gán cho ROLE_ADMIN
INSERT INTO permissions (name, description)
VALUES ('PAYMENT_READ', 'Xem lịch sử thanh toán toàn hệ thống')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PAYMENT_READ'
ON CONFLICT (role_id, permission_id) DO NOTHING;
