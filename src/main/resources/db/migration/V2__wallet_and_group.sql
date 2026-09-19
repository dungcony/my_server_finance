-- =============================================================
-- V2 — Module Ví tiền (Wallet Foundation)
-- =============================================================

-- -------------------------------------------------------------
-- 1. wallets — Ví tiền (Cá nhân)
-- -------------------------------------------------------------
CREATE TABLE wallets (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
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

    CONSTRAINT fk_wallets_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_wallets_type  CHECK (type IN ('cash', 'bank', 'e_wallet', 'credit_card')),
    CONSTRAINT ck_wallets_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

COMMENT ON TABLE  wallets                  IS 'Ví tiền của người dùng';
COMMENT ON COLUMN wallets.initial_balance  IS 'Số dư khởi tạo ban đầu khi tạo ví';
COMMENT ON COLUMN wallets.current_balance  IS 'Số dư hiện tại được cộng dồn theo giao dịch';
COMMENT ON COLUMN wallets.include_in_total IS 'Có tính vào tổng tài sản trên trang chủ không';

CREATE UNIQUE INDEX uq_wallets_user_name
    ON wallets (user_id, lower(name)) WHERE NOT is_deleted;
CREATE INDEX idx_wallets_user  ON wallets (user_id, sort_order) WHERE NOT is_deleted;

