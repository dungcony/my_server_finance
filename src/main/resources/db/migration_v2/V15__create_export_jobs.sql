-- =============================================================
-- Bảng export_jobs: quản lý các tiến trình và kết quả xuất báo cáo dữ liệu dạng tệp (CSV, Excel, PDF)
-- =============================================================

CREATE TABLE export_jobs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    format        VARCHAR(10)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'processing',
    file_path     VARCHAR(255),
    error_message TEXT,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_export_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_export_format CHECK (format IN ('csv', 'pdf', 'excel')),
    CONSTRAINT ck_export_status CHECK (status IN ('processing', 'completed', 'failed'))
);

COMMENT ON TABLE export_jobs IS 'Quản lý tiến trình xuất báo cáo tài chính bất đồng bộ (CSV, Excel, PDF)';
COMMENT ON COLUMN export_jobs.format IS 'Định dạng file xuất (csv, pdf, excel)';
COMMENT ON COLUMN export_jobs.status IS 'Trạng thái tiến trình (processing, completed, failed)';
COMMENT ON COLUMN export_jobs.expires_at IS 'Thời điểm link tải hết hạn (chỉ set khi completed)';

CREATE INDEX idx_export_user    ON export_jobs (user_id, created_at DESC);
CREATE INDEX idx_export_cleanup ON export_jobs (status, expires_at) WHERE status = 'completed';
CREATE INDEX idx_export_stuck   ON export_jobs (status, created_at) WHERE status = 'processing';
