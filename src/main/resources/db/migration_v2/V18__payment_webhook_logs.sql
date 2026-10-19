-- =============================================================
-- Bảng payment_webhook_logs: ghi nhận MỖI lần payOS gọi webhook (thành công lẫn lỗi) để admin xem trên dashboard.
-- Chỉ ghi thêm (append-only), không lưu body gốc và không lưu thông tin ngân hàng/tên người chuyển (dữ liệu cá nhân).
-- =============================================================

CREATE TABLE payment_webhook_logs (
    id              UUID         PRIMARY KEY,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    order_code      BIGINT,
    payment_link_id VARCHAR(100),
    amount          BIGINT,
    payos_code      VARCHAR(10),
    success         BOOLEAN,
    result          VARCHAR(30)  NOT NULL,
    message         VARCHAR(500),
    http_status     INT          NOT NULL,
    duration_ms     INT          NOT NULL,
    client_ip       VARCHAR(64),
    occurrences     INT          NOT NULL DEFAULT 1,
    aggregated      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_pwl_result CHECK (result IN ('PROCESSED', 'ALREADY_PAID', 'IGNORED_NOT_SUCCESS', 'IGNORED_EMPTY',
        'ORDER_NOT_FOUND', 'LINK_MISMATCH', 'ORDER_CLOSED', 'UNDERPAID', 'INVALID_BODY', 'INVALID_SIGNATURE', 'ERROR')),
    CONSTRAINT ck_pwl_occurrences CHECK (occurrences >= 1)
);

COMMENT ON TABLE payment_webhook_logs IS 'Nhật ký mỗi lần payOS gọi webhook, phục vụ dashboard admin';
COMMENT ON COLUMN payment_webhook_logs.result IS 'Kết quả xử lý, tên enum WebhookResult';
COMMENT ON COLUMN payment_webhook_logs.aggregated IS 'TRUE: dòng gộp nhiều request giả mạo (INVALID_*) vượt giới hạn mỗi phút; occurrences là số request đã gộp';

CREATE INDEX idx_pwl_received ON payment_webhook_logs (received_at DESC);
CREATE INDEX idx_pwl_order_code ON payment_webhook_logs (order_code) WHERE order_code IS NOT NULL;
CREATE INDEX idx_pwl_failed ON payment_webhook_logs (received_at DESC)
    WHERE result IN ('ORDER_NOT_FOUND', 'LINK_MISMATCH', 'ORDER_CLOSED', 'UNDERPAID', 'INVALID_BODY', 'INVALID_SIGNATURE', 'ERROR');
