-- =============================================================
-- V2 — Module Nhóm & Ví tiền (Wallet & Group Foundation)
-- =============================================================

-- -------------------------------------------------------------
-- 1. groups — Nhóm chung (Gia đình / Bạn bè / Ở ghép)
-- -------------------------------------------------------------
CREATE TABLE groups (
    id                     UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   VARCHAR(100) NOT NULL,
    created_by_id          UUID         NOT NULL,
    invite_code            VARCHAR(32)  NOT NULL,
    invite_code_expires_at TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_groups_creator FOREIGN KEY (created_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_groups_invite  UNIQUE (invite_code)
);

COMMENT ON TABLE  groups             IS 'Nhóm chung chia sẻ ví quỹ';
COMMENT ON COLUMN groups.invite_code IS 'Mã mời tham gia nhóm';

-- -------------------------------------------------------------
-- 2. group_members — Thành viên trong nhóm
-- -------------------------------------------------------------
CREATE TABLE group_members (
    id        UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id  UUID        NOT NULL,
    user_id   UUID        NOT NULL,
    role      VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_active BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_gm_group  FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_user   FOREIGN KEY (user_id)  REFERENCES users  (id) ON DELETE CASCADE,
    CONSTRAINT uq_gm_member UNIQUE (group_id, user_id),
    CONSTRAINT ck_gm_role   CHECK (role IN ('owner', 'member'))
);

COMMENT ON TABLE  group_members           IS 'Thành viên nhóm';
COMMENT ON COLUMN group_members.is_active IS 'Rời nhóm đặt FALSE để giữ lịch sử giao dịch';

CREATE INDEX idx_gm_user ON group_members (user_id) WHERE is_active;

-- -------------------------------------------------------------
-- 3. wallets — Ví tiền (Cá nhân hoặc Nhóm)
-- -------------------------------------------------------------
CREATE TABLE wallets (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID,
    group_id         UUID,
    name             VARCHAR(50)  NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    initial_balance  BIGINT       NOT NULL DEFAULT 0,
    current_balance  BIGINT       NOT NULL DEFAULT 0,
    include_in_total BOOLEAN      NOT NULL DEFAULT TRUE,
    icon             VARCHAR(50),
    color            CHAR(7),
    sort_order       INTEGER      NOT NULL DEFAULT 0,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_wallets_user  FOREIGN KEY (user_id)  REFERENCES users  (id) ON DELETE CASCADE,
    CONSTRAINT fk_wallets_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT ck_wallets_type  CHECK (type IN ('cash', 'bank', 'e_wallet', 'credit_card')),
    CONSTRAINT ck_wallets_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$'),

    -- Ví thuộc cá nhân HOẶC nhóm, không thể cả hai, không thể vô chủ
    CONSTRAINT ck_wallets_owner CHECK (
        (user_id IS NOT NULL AND group_id IS NULL) OR
        (user_id IS NULL     AND group_id IS NOT NULL)
    )
);

COMMENT ON TABLE  wallets                  IS 'Ví tiền của người dùng hoặc nhóm';
COMMENT ON COLUMN wallets.initial_balance  IS 'Số dư khởi tạo ban đầu khi tạo ví';
COMMENT ON COLUMN wallets.current_balance  IS 'Số dư hiện tại được cộng dồn theo giao dịch';
COMMENT ON COLUMN wallets.include_in_total IS 'Có tính vào tổng tài sản trên trang chủ không';

CREATE UNIQUE INDEX uq_wallets_user_name
    ON wallets (user_id, lower(name)) WHERE user_id IS NOT NULL AND NOT is_deleted;
CREATE UNIQUE INDEX uq_wallets_group_name
    ON wallets (group_id, lower(name)) WHERE group_id IS NOT NULL AND NOT is_deleted;
CREATE INDEX idx_wallets_user  ON wallets (user_id, sort_order) WHERE NOT is_deleted;
CREATE INDEX idx_wallets_group ON wallets (group_id)            WHERE group_id IS NOT NULL AND NOT is_deleted;
