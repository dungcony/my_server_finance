-- =============================================================
-- V1 — Cấu trúc các bảng (Tables DDL)
-- Sắp xếp theo thứ tự phụ thuộc khóa ngoại (Foreign Key Dependencies)
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- =============================================================
-- KHỐI NGƯỜI DÙNG & HẠ TẦNG XÁC THỰC
-- =============================================================

-- Bảng users — Tài khoản người dùng
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255),
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    avatar_url    TEXT,
    google_id     VARCHAR(255),
    plan          VARCHAR(20)  NOT NULL DEFAULT 'free',
    status        VARCHAR(20)  NOT NULL DEFAULT 'pending_verify',
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT uq_users_google_id UNIQUE (google_id),
    CONSTRAINT ck_users_email     CHECK (email = lower(email)),
    CONSTRAINT ck_users_plan      CHECK (plan IN ('free', 'premium')),
    CONSTRAINT ck_user_status     CHECK (status IN ('pending_verify', 'active', 'blocked'))
);

COMMENT ON TABLE users IS 'Tài khoản người dùng';
COMMENT ON COLUMN users.password_hash IS 'Bản băm (bcrypt), NULL nếu đăng nhập Google thuần';
COMMENT ON COLUMN users.google_id     IS 'Trường sub của Google ID token';
COMMENT ON COLUMN users.is_deleted    IS 'Xoá mềm, dữ liệu tài chính giữ nguyên';

-- Bảng roles — Vai trò hệ thống
CREATE TABLE roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL,
    level       INT          NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_roles_name UNIQUE (name)
);

COMMENT ON TABLE roles IS 'Vai trò hệ thống: ROLE_ADMIN, ROLE_USER';
COMMENT ON COLUMN roles.level IS 'Cấp bậc (1 = cao nhất, số càng nhỏ quyền càng cao)';

-- Bảng permissions — Quyền hạn chi tiết
CREATE TABLE permissions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_permissions_name UNIQUE (name)
);

COMMENT ON TABLE permissions IS 'Quyền hạn chi tiết cho các chức năng';

-- Bảng role_permissions — Bảng trung gian Role - Permission
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

-- Bảng user_roles — Bảng trung gian User - Role
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- Bảng refresh_tokens — Phiên đăng nhập
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    email       VARCHAR(255) NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    device_info VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_rt_hash UNIQUE (token_hash)
);

COMMENT ON TABLE refresh_tokens IS 'Phiên đăng nhập xoay vòng (token rotation)';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 của token gốc';
COMMENT ON COLUMN refresh_tokens.email IS 'Email chủ token, để thu hồi mọi phiên theo email (đặt lại mật khẩu)';

-- Bảng login_attempts — Nhật ký đăng nhập & khoá tạm thời
CREATE TABLE login_attempts (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL,
    ip_address   INET,
    succeeded    BOOLEAN      NOT NULL,
    user_agent   VARCHAR(255),
    attempted_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Bảng idempotency_keys — Chống ghi trùng request
CREATE TABLE idempotency_keys (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL,
    user_id         UUID         NOT NULL,
    endpoint        VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'processing',
    response_status INTEGER,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_idem_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_idem_scope  UNIQUE (idempotency_key, user_id, endpoint),
    CONSTRAINT ck_idem_status CHECK (status IN ('processing', 'completed'))
);

-- =============================================================
-- KHỐI VÍ & DANH MỤC THU CHI
-- =============================================================

-- Bảng wallets — Tài khoản ví tiền
CREATE TABLE wallets (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    balance          BIGINT       NOT NULL DEFAULT 0,
    icon             VARCHAR(50),
    color            CHAR(7),
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_wal_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_wal_type   CHECK (type IN ('cash', 'bank', 'e_wallet', 'credit_card')),
    CONSTRAINT ck_wal_color  CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

COMMENT ON TABLE wallets IS 'Ví tiền cá nhân (tiền mặt, thẻ, ví điện tử)';

-- Bảng icons — Kho biểu tượng hệ thống
CREATE TABLE icons (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(50)  NOT NULL,
    display_name    VARCHAR(100) NOT NULL,
    path_data       TEXT         NOT NULL,
    icon_group      VARCHAR(30)  NOT NULL,
    search_keywords TEXT,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default boolean not null default false,

    CONSTRAINT uq_icons_code  UNIQUE (code),
    CONSTRAINT ck_icons_group CHECK (icon_group IN (
        'an_uong', 'di_lai', 'mua_sam', 'giai_tri',
        'suc_khoe', 'hoc_tap', 'tai_chinh', 'khac'
    ))
);

COMMENT ON COLUMN wallets.is_default IS 'Ví mặc định của người dùng, nhận các khoản tự ghi từ giao dịch nhóm. Mỗi người dùng luôn có đúng một ví mặc định chưa xoá';
COMMENT ON COLUMN icons.path_data IS 'Đường vẽ SVG biểu tượng';

-- Bảng categories — Cây danh mục nhiều cấp, chỉ admin quản lý, dùng chung cho mọi người dùng
CREATE TABLE categories (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255),
    parent_id   UUID,
    icon_id     UUID         NOT NULL,
    color       CHAR(7)      NOT NULL,
    level       INTEGER      NOT NULL DEFAULT 0,
    is_leaf     BOOLEAN      NOT NULL DEFAULT TRUE,
    path        VARCHAR(255) NOT NULL,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_cat_code   UNIQUE (code),
    CONSTRAINT fk_cat_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cat_icon   FOREIGN KEY (icon_id)   REFERENCES icons      (id) ON DELETE RESTRICT,
    CONSTRAINT ck_cat_color  CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_cat_level  CHECK (level >= 0),
    CONSTRAINT ck_cat_parent CHECK (parent_id IS DISTINCT FROM id),
    CONSTRAINT ck_cat_root   CHECK ((parent_id IS NULL) = (level = 0))
);

COMMENT ON TABLE categories IS 'Cây danh mục thu chi nhiều cấp, không phân biệt thu/chi: không có cha thì là gốc';
COMMENT ON COLUMN categories.code      IS 'Mã duy nhất toàn hệ thống, kể cả danh mục đã xoá mềm (code xoá mềm vẫn giữ chỗ)';
COMMENT ON COLUMN categories.parent_id IS 'NULL = danh mục gốc';
COMMENT ON COLUMN categories.level     IS 'Độ sâu trong cây, gốc = 0';
COMMENT ON COLUMN categories.is_leaf   IS 'Còn con hay không, do service duy trì. Giao dịch chỉ gắn vào danh mục lá';
COMMENT ON COLUMN categories.path      IS 'Chuỗi code từ gốc tới danh mục, nối bằng dấu / (vd. an_uong/an_sang)';
COMMENT ON COLUMN categories.deleted   IS 'Xoá mềm, giao dịch cũ vẫn trỏ vào danh mục';

-- =============================================================
-- KHỐI GIAO DỊCH, GIAO DỊCH ĐỊNH KỲ & NGÂN SÁCH
-- =============================================================

-- Bảng recurring_transactions — Khoản thu/chi lặp lại định kỳ
CREATE TABLE recurring_transactions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    wallet_id     UUID         NOT NULL,
    category_id   UUID         NOT NULL,
    type          VARCHAR(10)  NOT NULL,
    amount        BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    note          TEXT,
    frequency     VARCHAR(10)  NOT NULL,
    "interval"    INTEGER      NOT NULL DEFAULT 1,
    start_date    DATE         NOT NULL,
    end_date      DATE,
    next_run_date DATE         NOT NULL,
    is_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_rec_user     FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_wallet   FOREIGN KEY (wallet_id)   REFERENCES wallets    (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_rec_type      CHECK (type IN ('EXPENSE', 'INCOME')),
    CONSTRAINT ck_rec_amount    CHECK (amount > 0),
    CONSTRAINT ck_rec_frequency CHECK (frequency IN ('WEEK', 'MONTH', 'QUARTER', 'YEAR')),
    CONSTRAINT ck_rec_interval  CHECK ("interval" >= 1),
    CONSTRAINT ck_rec_end_date  CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Bảng transactions — Giao dịch tài chính cá nhân
CREATE TABLE transactions (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    wallet_id    UUID         NOT NULL,
    category_id  UUID,
    type         VARCHAR(10)  NOT NULL,
    amount       BIGINT       NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    note         TEXT,
    name         VARCHAR(150),
    source       VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    recurring_id UUID,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_txn_user      FOREIGN KEY (user_id)      REFERENCES users                  (id) ON DELETE CASCADE,
    CONSTRAINT fk_txn_wallet    FOREIGN KEY (wallet_id)    REFERENCES wallets                (id) ON DELETE RESTRICT,
    CONSTRAINT fk_txn_category  FOREIGN KEY (category_id)  REFERENCES categories             (id) ON DELETE RESTRICT,
    CONSTRAINT fk_txn_recurring FOREIGN KEY (recurring_id) REFERENCES recurring_transactions (id) ON DELETE SET NULL,

    CONSTRAINT ck_txn_type   CHECK (type   IN ('EXPENSE', 'INCOME', 'LOAN')),
    CONSTRAINT ck_txn_source CHECK (source IN ('MANUAL', 'AI', 'RECURRING')),
    CONSTRAINT ck_txn_amount CHECK (amount > 0)
);

COMMENT ON TABLE transactions IS 'Giao dịch thu, chi, cho vay';

-- Bảng budgets — Hạn mức chi tiêu theo kỳ
CREATE TABLE budgets (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    category_id  UUID        NOT NULL,
    spent        BIGINT      NOT NULL DEFAULT 0,
    limit_amount BIGINT      NOT NULL,
    period_type  VARCHAR(10) NOT NULL,
    start_at     TIMESTAMPTZ NOT NULL,
    end_at       TIMESTAMPTZ NOT NULL,
    auto_renew   BOOLEAN     NOT NULL DEFAULT FALSE,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_bud_user     FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE CASCADE,
    CONSTRAINT fk_bud_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_bud_spent    CHECK (spent >= 0),
    CONSTRAINT ck_bud_amount   CHECK (limit_amount > 0),
    CONSTRAINT ck_bud_period   CHECK (period_type IN ('WEEK', 'MONTH', 'QUARTER', 'YEAR')),
    CONSTRAINT ck_bud_dates    CHECK (end_date >= start_date),
    CONSTRAINT ex_bud_no_overlap EXCLUDE USING gist (
        user_id WITH =,
        category_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (is_active)
);

COMMENT ON COLUMN budgets.spent       IS 'Đã chi trong kỳ, BudgetSpendService cộng trừ nguyên tử khi giao dịch chi thay đổi';
COMMENT ON COLUMN budgets.period_type IS 'Tên enum PeriodType (viết hoa), lưu theo @Enumerated(STRING)';

-- =============================================================
-- KHỐI CÔNG NỢ & MỤC TIÊU TIẾT KIỆM
-- =============================================================

-- Bảng debts — Sổ ghi nợ và cho vay
CREATE TABLE debts (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL,
    wallet_id             UUID         NOT NULL,
    type                  VARCHAR(10)  NOT NULL,
    counterparty_name     VARCHAR(100) NOT NULL,
    principal_amount      BIGINT       NOT NULL,
    paid_amount           BIGINT       NOT NULL DEFAULT 0,
    issued_date           DATE         NOT NULL,
    due_date              DATE,
    note                  TEXT,
    status                VARCHAR(20)  NOT NULL DEFAULT 'outstanding',
    origin_transaction_id UUID         NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_debt_user   FOREIGN KEY (user_id)               REFERENCES users        (id) ON DELETE CASCADE,
    CONSTRAINT fk_debt_wallet FOREIGN KEY (wallet_id)             REFERENCES wallets      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debt_txn    FOREIGN KEY (origin_transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_debt_type      CHECK (type IN ('lending', 'borrowing')),
    CONSTRAINT ck_debt_principal CHECK (principal_amount > 0),
    CONSTRAINT ck_debt_paid      CHECK (paid_amount >= 0),
    CONSTRAINT ck_debt_status    CHECK (status IN ('outstanding', 'settled', 'written_off')),
    CONSTRAINT ck_debt_due_date  CHECK (due_date IS NULL OR due_date >= issued_date)
);

COMMENT ON COLUMN debts.origin_transaction_id IS 'Giao dịch gốc sinh ra khoản nợ này';

-- Bảng debt_payments — Lịch sử các đợt trả nợ
CREATE TABLE debt_payments (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    debt_id        UUID        NOT NULL,
    transaction_id UUID        NOT NULL,
    amount         BIGINT      NOT NULL,
    paid_date      DATE        NOT NULL,
    note           TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_dp_debt   FOREIGN KEY (debt_id)        REFERENCES debts        (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_txn    FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_dp_amount CHECK (amount > 0),
    CONSTRAINT uq_dp_txn    UNIQUE (transaction_id)
);

-- Bảng savings_goals — Mục tiêu tích luỹ tài chính
CREATE TABLE savings_goals (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    wallet_id     UUID,
    name          VARCHAR(100) NOT NULL,
    target_amount BIGINT       NOT NULL,
    saved_amount  BIGINT       NOT NULL DEFAULT 0,
    target_date   DATE,
    icon_id       UUID,
    status        VARCHAR(20)  NOT NULL DEFAULT 'in_progress',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_goal_user   FOREIGN KEY (user_id)   REFERENCES users   (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE SET NULL,
    CONSTRAINT fk_goal_icon   FOREIGN KEY (icon_id)   REFERENCES icons   (id) ON DELETE SET NULL,
    CONSTRAINT ck_goal_target CHECK (target_amount > 0),
    CONSTRAINT ck_goal_saved  CHECK (saved_amount >= 0),
    CONSTRAINT ck_goal_status CHECK (status IN ('in_progress', 'completed', 'cancelled'))
);

-- Bảng goal_contributions — Lịch sử các lần đóng góp vào mục tiêu
CREATE TABLE goal_contributions (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id          UUID        NOT NULL,
    transaction_id   UUID,
    amount           BIGINT      NOT NULL,
    contributed_date DATE        NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_gc_goal   FOREIGN KEY (goal_id)        REFERENCES savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_gc_txn    FOREIGN KEY (transaction_id) REFERENCES transactions  (id) ON DELETE SET NULL,
    CONSTRAINT ck_gc_amount CHECK (amount > 0)
);

-- =============================================================
-- KHỐI THÔNG BÁO & BÁO CÁO
-- =============================================================

-- Bảng notifications — Thông báo trong ứng dụng
CREATE TABLE notifications (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    title        VARCHAR(150) NOT NULL,
    content      TEXT         NOT NULL,
    data         jsonb,
    reference_id UUID,
    is_read      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
);

COMMENT ON COLUMN notifications.type IS 'Loại thông báo (tên hằng NotiType), app dựa vào đây để chọn icon và màn hình đích';
COMMENT ON COLUMN notifications.reference_id IS 'Id đối tượng chính thông báo nhắc tới (ngân sách, khoản nợ, giao dịch nhóm...), tuỳ type; không có khoá ngoại';
COMMENT ON COLUMN notifications.data IS 'Dữ liệu phụ để app điều hướng, json phẳng, null nếu không cần; không chứa trạng thái. VD thông báo giao dịch nhóm: {"action": "...", "group_id": "..."}';

-- Bảng export_jobs — Tác vụ xuất báo cáo bất đồng bộ
CREATE TABLE export_jobs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL,
    format        VARCHAR(10) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'processing',
    file_path     VARCHAR(255),
    error_message TEXT,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_exp_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_export_format CHECK (format IN ('csv', 'pdf', 'excel')),
    CONSTRAINT ck_export_status CHECK (status IN ('processing', 'completed', 'failed'))
);

-- =============================================================
-- KHỐI NHÓM CHUNG QUỸ (GROUP)
-- =============================================================

-- Bảng groups — Thông tin nhóm chung
CREATE TABLE groups (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(255),
    invite_code             VARCHAR(32)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    target                  BIGINT,
    is_settlement_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_join_without_confirm BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_groups_invite_code UNIQUE (invite_code),
    CONSTRAINT uq_groups_invite CHECK (invite_code <> ''),
    CONSTRAINT ck_groups_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
    CONSTRAINT ck_groups_target CHECK (target IS NULL OR (target > 0 AND target <= 999999999999))
);

COMMENT ON TABLE groups IS 'Nhóm chung chia sẻ quỹ chi tiêu theo mô hình sổ nhóm độc lập';

-- Bảng group_members — Thành viên nhóm và lịch sử tham gia
CREATE TABLE group_members (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id  UUID        NOT NULL,
    user_id   UUID        NOT NULL,
    role      VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status    VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ,
    left_at   TIMESTAMPTZ,

    CONSTRAINT fk_gm_group  FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user   FOREIGN KEY (user_id)  REFERENCES users  (id) ON DELETE CASCADE,
    CONSTRAINT ck_gm_role   CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT ck_gm_status CHECK (status IN ('PENDING', 'ACTIVE', 'LEFT', 'REMOVED')),
    CONSTRAINT ck_gm_dates  CHECK (
        (status = 'PENDING' AND joined_at IS NULL     AND left_at IS NULL)
     OR (status = 'ACTIVE'  AND joined_at IS NOT NULL AND left_at IS NULL)
     OR (status IN ('LEFT', 'REMOVED') AND joined_at IS NOT NULL AND left_at IS NOT NULL)
    )
);

COMMENT ON TABLE group_members IS 'Thành viên nhóm; lưu khoảng thời gian để xác định thành viên có mặt vào thời điểm giao dịch';

-- Bảng group_funds — Quỹ nhóm duy nhất (Gắn với thủ quỹ)
CREATE TABLE group_funds (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID        NOT NULL,
    keepper_id      UUID        NOT NULL,
    current_balance BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_gf_group   FOREIGN KEY (group_id)   REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gf_keepper FOREIGN KEY (keepper_id) REFERENCES users  (id) ON DELETE RESTRICT,
    CONSTRAINT uq_group_funds_group UNIQUE (group_id)
);

COMMENT ON TABLE group_funds IS 'Quỹ duy nhất của nhóm, giao cho một thủ quỹ cầm giữ';

-- Bảng group_transactions — Giao dịch tài chính của nhóm
CREATE TABLE group_transactions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id      UUID         NOT NULL,
    money_source  VARCHAR(20)  NOT NULL,
    transactor_id UUID,
    created_by    UUID         NOT NULL,
    category_id   UUID,
    type          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by   UUID,
    reviewed_at   TIMESTAMPTZ,
    amount        BIGINT       NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    note          VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT fk_gt_group      FOREIGN KEY (group_id)      REFERENCES groups     (id) ON DELETE CASCADE,
    CONSTRAINT fk_gt_transactor FOREIGN KEY (transactor_id) REFERENCES users      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_creator    FOREIGN KEY (created_by)    REFERENCES users      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_category   FOREIGN KEY (category_id)   REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_reviewer   FOREIGN KEY (reviewed_by)   REFERENCES users      (id) ON DELETE RESTRICT,

    CONSTRAINT ck_gt_amount       CHECK (amount > 0 AND amount <= 999999999999),
    CONSTRAINT ck_gt_type         CHECK (type IN ('EXPENSE', 'CONTRIBUTION', 'REFUND', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN')),
    CONSTRAINT ck_gt_money_source CHECK (money_source IN ('FUND', 'PERSONAL')),
    CONSTRAINT ck_gt_status       CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_gt_review       CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
     OR (status IN ('CONFIRMED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_gt_treasurer_confirmed CHECK (
        type NOT IN ('REFUND', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN') OR status = 'CONFIRMED'
    ),
    CONSTRAINT ck_gt_shape CHECK (
        (type = 'EXPENSE' AND category_id IS NOT NULL)
     OR (type = 'CONTRIBUTION' AND money_source = 'PERSONAL' AND category_id IS NULL)
     OR (type IN ('REFUND', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN') AND money_source = 'FUND' AND category_id IS NULL)
    ),
    CONSTRAINT ck_gt_transactor CHECK (
        (money_source = 'PERSONAL' AND transactor_id IS NOT NULL)
     OR (money_source = 'FUND' AND transactor_id IS NULL)
    )
);

COMMENT ON TABLE group_transactions IS 'Giao dịch thu, chi, góp quỹ, kiểm kê của nhóm';
COMMENT ON COLUMN group_transactions.transactor_id IS 'Người bỏ tiền túi (chi tiêu bằng tiền cá nhân, góp quỹ); NULL khi dùng tiền quỹ (hoàn tiền, chi từ quỹ, kiểm kê)';
COMMENT ON COLUMN group_transactions.version IS 'Khoá lạc quan: tăng 1 mỗi lần ghi, chặn hai request duyệt/sửa cùng lúc';

-- Bảng group_transaction_participants — Người cùng chia tiền
CREATE TABLE group_transaction_participants (
    group_transaction_id UUID   NOT NULL,
    user_id              UUID   NOT NULL,
    share_amount         BIGINT NOT NULL,

    PRIMARY KEY (group_transaction_id, user_id),
    CONSTRAINT fk_gtp_transaction FOREIGN KEY (group_transaction_id) REFERENCES group_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_gtp_user        FOREIGN KEY (user_id)              REFERENCES users              (id) ON DELETE CASCADE,
    CONSTRAINT ck_gtp_share_amount CHECK (share_amount > 0 AND share_amount <= 999999999999)
);

COMMENT ON TABLE group_transaction_participants IS 'Người cùng chịu chi phí trong giao dịch nhóm';

-- Bảng group_member_balances — Số dư tích luỹ của từng thành viên
CREATE TABLE group_member_balances (
    group_id           UUID   NOT NULL,
    user_id            UUID   NOT NULL,
    paid_out_of_pocket BIGINT NOT NULL DEFAULT 0,
    contribution       BIGINT NOT NULL DEFAULT 0,
    refund             BIGINT NOT NULL DEFAULT 0,
    share              BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_gmb_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gmb_user  FOREIGN KEY (user_id)  REFERENCES users  (id) ON DELETE CASCADE
);

COMMENT ON TABLE group_member_balances IS 'Số dư tích luỹ của từng thành viên trong nhóm, phục vụ báo cáo và kiểm tra hạn mức';
