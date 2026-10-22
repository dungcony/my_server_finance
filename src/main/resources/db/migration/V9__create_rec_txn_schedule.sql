-- =============================================================
-- V9 — Tạo bảng recurring_transaction_schedules và các chỉ mục
-- =============================================================

CREATE TABLE recurring_transaction_schedules (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recurring_id UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    run_at       TIMESTAMPTZ NOT NULL,
    amount       BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_deleted   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_rec_sched_rec  FOREIGN KEY (recurring_id) REFERENCES recurring_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_sched_user FOREIGN KEY (user_id)      REFERENCES users                  (id) ON DELETE CASCADE,
    CONSTRAINT ck_rec_sched_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_rec_sched_amount CHECK (amount > 0)
);

COMMENT ON TABLE recurring_transaction_schedules IS 'Lịch phát sinh dự kiến cho từng kỳ của giao dịch định kỳ';

CREATE INDEX idx_rec_sched_rec_id ON recurring_transaction_schedules (recurring_id);
CREATE INDEX idx_rec_sched_user_run_at ON recurring_transaction_schedules (user_id, run_at);
CREATE INDEX idx_rec_sched_run_at_status ON recurring_transaction_schedules (run_at, status);
