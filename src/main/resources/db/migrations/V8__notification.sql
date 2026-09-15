-- =============================================================
-- V8 — Module Thông báo trong ứng dụng (Notification)
-- =============================================================

CREATE TABLE notifications (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    type         VARCHAR(30)  NOT NULL,
    title        VARCHAR(150) NOT NULL,
    content      TEXT         NOT NULL,
    reference_id UUID,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notif_type CHECK (type IN (
        'budget_alert', 'debt_reminder', 'recurring_generated', 'budget_renewed', 'goal_completed'
    ))
);

COMMENT ON TABLE  notifications              IS 'Hộp thư thông báo trong ứng dụng';
COMMENT ON COLUMN notifications.reference_id IS 'ID tham chiếu tới budget, debt hoặc giao dịch liên quan';

CREATE INDEX idx_notif_user ON notifications (user_id, created_at DESC);

-- Chống trùng cảnh báo trong ngày (quy về ngày UTC để biểu thức immutable)
CREATE UNIQUE INDEX uq_notif_budget_alert
    ON notifications (user_id, reference_id, ((created_at AT TIME ZONE 'UTC')::date), type);
