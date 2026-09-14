-- =============================================================
-- V1 — Module User, Phân quyền & Hạ tầng xác thực
-- Ứng dụng Quản lý tài chính cá nhân có AI
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- cho gen_random_uuid()

-- -------------------------------------------------------------
-- 1. users — Tài khoản người dùng
-- -------------------------------------------------------------
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(100) NOT NULL,
    password_hash   VARCHAR(255),
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    avatar_url      TEXT,
    google_id       VARCHAR(255),
    plan            VARCHAR(20)  NOT NULL DEFAULT 'free',
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending_verify',
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_confirm      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_blocked      BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ,

    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT uq_users_google_id UNIQUE (google_id),
    CONSTRAINT ck_users_email     CHECK (email = lower(email)),
    CONSTRAINT ck_users_plan      CHECK (plan IN ('free', 'premium')),
    CONSTRAINT ck_user_status     CHECK (status IN ('pending_verify', 'active', 'blocked')),
    CONSTRAINT ck_users_role      CHECK (role IN ('USER', 'ADMIN'))
);

COMMENT ON TABLE  users               IS 'Tài khoản người dùng';
COMMENT ON COLUMN users.password_hash IS 'Bản băm (bcrypt), NULL nếu đăng nhập Google thuần';
COMMENT ON COLUMN users.google_id     IS 'Trường sub của Google ID token';
COMMENT ON COLUMN users.role          IS 'Vai trò cấp hệ thống: USER, ADMIN';
COMMENT ON COLUMN users.is_confirm    IS 'Đã xác thực email chưa';
COMMENT ON COLUMN users.is_blocked    IS 'ADMIN khoá vĩnh viễn';
COMMENT ON COLUMN users.is_deleted    IS 'Xoá mềm, dữ liệu tài chính giữ nguyên';

CREATE INDEX idx_users_admin ON users (id) WHERE role = 'ADMIN';

-- -------------------------------------------------------------
-- 2. roles — Vai trò hệ thống
-- -------------------------------------------------------------
CREATE TABLE roles (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50)  NOT NULL,
    level       INT          NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_roles_name UNIQUE (name)
);

COMMENT ON TABLE  roles       IS 'Vai trò hệ thống: ROLE_ADMIN, ROLE_USER';
COMMENT ON COLUMN roles.level IS 'Cấp bậc (1 = cao nhất, số càng nhỏ quyền càng cao)';

-- -------------------------------------------------------------
-- 3. permissions — Quyền hạn chi tiết
-- -------------------------------------------------------------
CREATE TABLE permissions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_permissions_name UNIQUE (name)
);

COMMENT ON TABLE permissions IS 'Quyền hạn chi tiết cho các chức năng';

-- -------------------------------------------------------------
-- 4. role_permissions — Bảng trung gian Role - Permission
-- -------------------------------------------------------------
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);

-- -------------------------------------------------------------
-- 5. user_roles — Bảng trung gian User - Role
-- -------------------------------------------------------------
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- -------------------------------------------------------------
-- 6. refresh_tokens — Phiên đăng nhập (30 ngày)
-- -------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    device_info VARCHAR(255),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_rt_hash UNIQUE (token_hash)
);

COMMENT ON TABLE  refresh_tokens            IS 'Phiên đăng nhập xoay vòng (token rotation)';
COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 của token gốc';

CREATE INDEX idx_rt_active  ON refresh_tokens (token_hash) WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_user    ON refresh_tokens (user_id)    WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_cleanup ON refresh_tokens (expires_at);

-- -------------------------------------------------------------
-- 7. login_attempts — Nhật ký đăng nhập & khoá tạm thời
-- -------------------------------------------------------------
CREATE TABLE login_attempts (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL,
    ip_address   INET,
    succeeded    BOOLEAN      NOT NULL,
    user_agent   VARCHAR(255),
    attempted_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_la_email   ON login_attempts (email, attempted_at DESC) WHERE NOT succeeded;
CREATE INDEX idx_la_ip      ON login_attempts (ip_address, attempted_at DESC) WHERE NOT succeeded;
CREATE INDEX idx_la_cleanup ON login_attempts (attempted_at);

-- -------------------------------------------------------------
-- 8. idempotency_keys — Chống ghi trùng request
-- -------------------------------------------------------------
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

CREATE INDEX idx_idem_cleanup ON idempotency_keys (created_at);

-- =============================================================
-- SEED DATA: Roles, Permissions, Admin mặc định
-- =============================================================
INSERT INTO roles (name, level, description) VALUES
    ('ROLE_ADMIN', 1,  'Quản trị viên toàn quyền hệ thống'),
    ('ROLE_USER',  10, 'Người dùng ứng dụng thông thường')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, description) VALUES
    ('users:read',              'Xem danh sách và chi tiết người dùng'),
    ('users:update',            'Khóa, mở khóa hoặc cập nhật thông tin người dùng'),
    ('users:delete',            'Xoá tài khoản người dùng'),
    ('roles:read',              'Xem danh sách vai trò và quyền hạn'),
    ('roles:update',            'Sửa thông tin vai trò (tên, mô tả, cấp bậc)'),
    ('roles:delete',            'Xoá vai trò'),
    ('user_role:read',          'Xem vai trò đã gán cho người dùng'),
    ('user_role:update',        'Gán vai trò cho người dùng'),
    ('user_role:delete',        'Thu hồi vai trò đã gán của người dùng'),
    ('role_permission:read',    'Xem quyền hạn đã gán cho vai trò'),
    ('role_permission:update',  'Gán quyền hạn cho vai trò'),
    ('role_permission:delete',  'Thu hồi quyền hạn đã gán của vai trò'),
    ('categories:manage',       'Quản lý danh mục và biểu tượng mặc định hệ thống'),
    ('system:view_stats',       'Xem báo cáo thống kê toàn hệ thống')
ON CONFLICT (name) DO NOTHING;

-- Gán toàn bộ quyền quản trị cho ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Tài khoản Quản trị viên (Admin) mặc định: admin@financeapp.com / Admin@123
INSERT INTO users (
    email,
    password_hash,
    first_name,
    last_name,
    plan,
    status,
    role,
    is_confirm,
    is_blocked,
    is_deleted,
    created_at
) VALUES (
    'admin@financeapp.com',
    '$2a$12$7humJ1hfArXzq9mpOqwJLuY46gx7wAgg4RJgu8Ht3vi/Qwb4qx60G',
    'Hệ thống',
    'Quản trị viên',
    'premium',
    'active',
    'ADMIN',
    true,
    false,
    false,
    now()
)
ON CONFLICT (email) DO NOTHING;

-- Gán quyền cho tài khoản Admin
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email = 'admin@financeapp.com'
  AND r.name IN ('ROLE_ADMIN', 'ROLE_USER')
ON CONFLICT (user_id, role_id) DO NOTHING;
