-- =============================================================
-- V5 — Thông báo (Notification)
-- =============================================================

CREATE TABLE notifications (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    title        VARCHAR(150) NOT NULL,
    content      TEXT         NOT NULL,
    data         JSONB,
    reference_id UUID,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE notifications IS 'Hộp thư thông báo trong app';
COMMENT ON COLUMN notifications.type IS 'Loại thông báo (tên hằng NotiType), app dựa vào đây để chọn icon và màn hình đích';
COMMENT ON COLUMN notifications.reference_id IS 'Id đối tượng chính thông báo nhắc tới, không có khoá ngoại';
COMMENT ON COLUMN notifications.data IS 'Dữ liệu phụ để app điều hướng, json phẳng, null nếu không cần';

CREATE INDEX idx_notif_user_created ON notifications (user_id, created_at DESC);
CREATE UNIQUE INDEX uq_notif_user_ref_day_type
ON notifications (user_id, reference_id, ((created_at AT TIME ZONE 'UTC')::date), type);