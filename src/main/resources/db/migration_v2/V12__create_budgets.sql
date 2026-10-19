-- Bảng quản lý ngân sách chi tiêu của người dùng theo danh mục và kỳ thời gian
CREATE TABLE budgets (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    category_code VARCHAR(50)  NOT NULL, -- Mã danh mục chi tiêu liên kết với bảng categories
    spent         BIGINT       NOT NULL DEFAULT 0, -- Số tiền đã chi trong kỳ (VND)
    limit_amount  BIGINT       NOT NULL, -- Hạn mức ngân sách đề ra (VND)
    period_type   VARCHAR(10)  NOT NULL, -- Kỳ ngân sách: 'WEEK', 'MONTH', 'QUARTER', 'YEAR'
    start_date    DATE         NOT NULL, -- Ngày bắt đầu kỳ (theo lịch địa phương của user)
    end_date      DATE         NOT NULL, -- Ngày kết thúc kỳ (theo lịch địa phương của user)
    auto_renew    BOOLEAN      NOT NULL DEFAULT FALSE, -- Tự động tạo ngân sách mới khi hết kỳ
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,  -- Trạng thái đang áp dụng (true) hoặc tạm dừng (false)
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE, -- Cờ xóa mềm (AssignedIdEntity)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_bud_user     FOREIGN KEY (user_id)       REFERENCES users      (id) ON DELETE CASCADE,
    CONSTRAINT fk_bud_category FOREIGN KEY (category_code) REFERENCES categories (code) ON DELETE RESTRICT,
    CONSTRAINT ck_bud_spent    CHECK (spent >= 0),
    CONSTRAINT ck_bud_limit    CHECK (limit_amount > 0),
    CONSTRAINT ck_bud_period   CHECK (period_type IN ('WEEK', 'MONTH', 'QUARTER', 'YEAR')),
    CONSTRAINT ck_bud_dates    CHECK (end_date >= start_date)
);

-- Index phục vụ tìm kiếm ngân sách đang hoạt động của người dùng
CREATE INDEX idx_bud_user ON budgets (user_id, is_active) WHERE NOT is_deleted;
-- Index phục vụ job tự động gia hạn ngân sách hết hạn hàng đêm
CREATE INDEX idx_bud_renew ON budgets (end_date) WHERE auto_renew AND is_active AND NOT is_deleted;
-- Index phục vụ cập nhật nhanh số tiền spent khi có giao dịch chi tiêu theo danh mục
CREATE INDEX idx_bud_category ON budgets (user_id, category_code) WHERE is_active AND NOT is_deleted;
