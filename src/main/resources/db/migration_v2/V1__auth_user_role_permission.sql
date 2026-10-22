-- =============================================================
-- Người dùng, vai trò, quyền và hạ tầng xác thực
-- =============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- users --
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
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT uq_users_google_id UNIQUE (google_id),
    CONSTRAINT ck_users_email     CHECK (email = lower(email)),
    CONSTRAINT ck_users_plan      CHECK (plan IN ('free', 'premium')),
    CONSTRAINT ck_users_status    CHECK (status IN ('pending_verify', 'active', 'blocked'))
);

COMMENT ON COLUMN users.password_hash IS 'Bản băm bcrypt, NULL nếu chỉ đăng nhập Google';
COMMENT ON COLUMN users.google_id     IS 'Trường sub của Google ID token';
COMMENT ON COLUMN users.plan          IS 'Tên enum UserPlan viết thường (LowercaseEnumConverter)';
COMMENT ON COLUMN users.status        IS 'Tên enum UserStatus viết thường (LowercaseEnumConverter)';

-- roles --
CREATE TABLE roles (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL,
    level       INTEGER     NOT NULL,
    description TEXT,
    is_deleted  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_roles_name UNIQUE (name)
);

COMMENT ON COLUMN roles.name  IS 'Tên enum RoleName: ROLE_ADMIN, ROLE_USER';
COMMENT ON COLUMN roles.level IS 'Cấp bậc, số càng nhỏ quyền càng cao (1 = cao nhất)';

-- permissions --
CREATE TABLE permissions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_permissions_name UNIQUE (name)
);

-- role_permissions --
CREATE TABLE role_permissions (
    role_id       UUID NOT NULL,
    permission_id UUID NOT NULL,

    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles (id)       ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);

-- user_roles --
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

-- refresh_tokens --
CREATE TABLE refresh_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    email       VARCHAR(255) NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    device_info VARCHAR(255),
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_rt_hash UNIQUE (token_hash)
);

COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 của token gốc';
COMMENT ON COLUMN refresh_tokens.email      IS 'Email chủ token, để thu hồi mọi phiên theo email khi đặt lại mật khẩu';

CREATE INDEX idx_rt_user    ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_email   ON refresh_tokens (email) WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_cleanup ON refresh_tokens (expires_at);

-- login_attempts --
CREATE TABLE login_attempts (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL,
    ip_address   INET,
    succeeded    BOOLEAN      NOT NULL,
    user_agent   VARCHAR(255),
    attempted_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_la_email   ON login_attempts (email, attempted_at DESC) WHERE NOT succeeded;
CREATE INDEX idx_la_ip      ON login_attempts (ip_address, attempted_at DESC) WHERE NOT succeeded;
CREATE INDEX idx_la_cleanup ON login_attempts (attempted_at);

-- idempotency_keys: chống ghi trùng request (dùng cho @Idempotent) --
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
-- SEED: vai trò và quyền hạn
-- permissions.name phải khớp PermissionName.getValue() ("{resource}:{action}"),
-- giá trị lạ sẽ làm PermissionNameConverter ném lỗi khi đọc lên
-- =============================================================

INSERT INTO roles (name, level, description)
VALUES ('ROLE_ADMIN', 1, 'Quản trị viên toàn quyền hệ thống'),
       ('ROLE_MANAGER', 5, 'Người quản lý, quyền do admin gán thêm'),
       ('ROLE_USER', 10, 'Người dùng ứng dụng thông thường')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, description)
VALUES ('users:read', 'Xem danh sách và chi tiết người dùng'),
       ('users:update', 'Khóa, mở khóa hoặc cập nhật thông tin người dùng'),
       ('users:delete', 'Xoá tài khoản người dùng'),
       ('roles:read', 'Xem danh sách vai trò và quyền hạn'),
       ('roles:update', 'Sửa thông tin vai trò (tên, mô tả, cấp bậc)'),
       ('roles:delete', 'Xoá vai trò'),
       ('user_role:read', 'Xem vai trò đã gán cho người dùng'),
       ('user_role:update', 'Gán vai trò cho người dùng'),
       ('user_role:delete', 'Thu hồi vai trò đã gán của người dùng'),
       ('role_permission:read', 'Xem quyền hạn đã gán cho vai trò'),
       ('role_permission:update', 'Gán quyền hạn cho vai trò'),
       ('role_permission:delete', 'Thu hồi quyền hạn đã gán của vai trò'),
       ('categories:update', 'Tạo và cập nhật danh mục hệ thống'),
       ('categories:delete', 'Xóa danh mục hệ thống'),
       ('group:read', 'Xem toàn bộ nhóm'),
       ('icons:update', 'Tạo và cập nhật biểu tượng hệ thống'),
       ('icons:delete', 'Xoá biểu tượng hệ thống'),
       ('recurring_txn:read', 'Xem giao dịch định kỳ của người dùng'),
       ('recurring_txn:delete', 'Xoá giao dịch định kỳ của người dùng'),
       ('wallet:read', 'Xem ví của người dùng'),
       ('wallet:update', 'Tạo và cập nhật ví của người dùng'),
       ('wallet:delete', 'Xoá ví của người dùng'),
       ('transactions:read', 'Xem giao dịch của người dùng'),
       ('transactions:delete', 'Xoá giao dịch của người dùng')
ON CONFLICT (name) DO NOTHING;

-- gán toàn bộ quyền cho ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- =============================================================
-- SEED: tài khoản quản trị viên mặc định (mật khẩu như seed admin cũ ở db/migration/V4)
-- =============================================================

INSERT INTO users (email, password_hash, first_name, last_name, plan, status)
VALUES ('admin@financeapp.com',
        '$2a$12$7humJ1hfArXzq9mpOqwJLuY46gx7wAgg4RJgu8Ht3vi/Qwb4qx60G',
        'Hệ thống',
        'Quản trị viên',
        'premium',
        'active')
ON CONFLICT (email) DO NOTHING;

-- admin mang cả ROLE_ADMIN và ROLE_USER để dùng được cả luồng người dùng thường
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.email = 'admin@financeapp.com'
  AND r.name IN ('ROLE_ADMIN', 'ROLE_USER')
ON CONFLICT (user_id, role_id) DO NOTHING;
