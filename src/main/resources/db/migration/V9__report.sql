-- =============================================================
-- V9 — Module Tác vụ xuất báo cáo (Report & Export Jobs)
-- =============================================================

CREATE TABLE export_jobs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,
    format        VARCHAR(10) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'processing',
    file_path     TEXT,
    error_message TEXT,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_export_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_export_format CHECK (format IN ('csv', 'pdf', 'excel')),
    CONSTRAINT ck_export_status CHECK (status IN ('processing', 'completed', 'failed'))
);

COMMENT ON TABLE  export_jobs           IS 'Tác vụ xuất báo cáo bất đồng bộ';
COMMENT ON COLUMN export_jobs.file_path IS 'Đường dẫn tệp trên đĩa máy chủ';

CREATE INDEX idx_export_user    ON export_jobs (user_id, created_at DESC);
CREATE INDEX idx_export_expires ON export_jobs (expires_at) WHERE status = 'completed';
