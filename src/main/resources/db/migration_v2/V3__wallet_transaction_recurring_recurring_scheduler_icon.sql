-- bảng icons và categories nằm ở V2__categories.sql --

-- wallets --
CREATE TABLE wallets (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL,
    name       VARCHAR(50) NOT NULL,
    type       VARCHAR(20) NOT NULL,
    balance    BIGINT      NOT NULL DEFAULT 0,
    icon_code  char(50),
    color      CHAR(7),
    sort_order INTEGER     NOT NULL DEFAULT 0,
    is_default BOOLEAN     NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_wal_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wal_icon  FOREIGN KEY (icon_code) REFERENCES icons (code) ON DELETE SET NULL,
   -- CONSTRAINT ck_wal_type  CHECK (type IN ('CASH', 'BANK', 'E_WALLET')),
    CONSTRAINT ck_wal_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);
-- tên ví không trùng trong cùng một người dùng, phân biệt hoa thường, bỏ qua ví đã xoá
CREATE UNIQUE INDEX uq_wallets_user_name ON wallets (user_id, name) WHERE NOT is_deleted;
CREATE INDEX idx_wallets_user ON wallets (user_id, sort_order) WHERE NOT is_deleted;

-- transactions: giao dịch thu, chi, cho vay của cá nhân --
-- số dư ví do trigger trg_transactions_wallet_balance (V6) cập nhật theo bảng này
CREATE TABLE transactions (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    wallet_id   UUID         NOT NULL,
    category_id UUID,
    type        VARCHAR(10)  NOT NULL,
    amount      BIGINT       NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    name        VARCHAR(150),
    note        TEXT,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_txn_user     FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE CASCADE,
    CONSTRAINT fk_txn_wallet   FOREIGN KEY (wallet_id)   REFERENCES wallets    (id),
    CONSTRAINT fk_txn_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_txn_type     CHECK (type IN ('EXPENSE', 'INCOME', 'LOAN')),
    CONSTRAINT ck_txn_source   CHECK (source IN ('MANUAL', 'RECURRING')),
    CONSTRAINT ck_txn_amount   CHECK (amount > 0)
);

COMMENT ON COLUMN transactions.category_id IS 'NULL với giao dịch "Số dư ban đầu" do trigger sinh';
COMMENT ON COLUMN transactions.source      IS 'Tên enum TxnSource: MANUAL (nhập tay), RECURRING (sinh từ giao dịch định kỳ)';

CREATE INDEX idx_txn_user_date     ON transactions (user_id, occurred_at DESC) WHERE NOT is_deleted;
CREATE INDEX idx_txn_wallet_date   ON transactions (wallet_id, occurred_at) WHERE NOT is_deleted;
CREATE INDEX idx_txn_category_date ON transactions (category_id, occurred_at) WHERE NOT is_deleted AND category_id IS NOT NULL;

-- recurring_transactions: cấu hình giao dịch định kỳ (entity RecTxn) --
CREATE TABLE recurring_transactions (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    wallet_id       UUID         NOT NULL,
    category_id     UUID,
    name            VARCHAR(100) NOT NULL,
    type            VARCHAR(10)  NOT NULL,
    amount          BIGINT       NOT NULL,
    note            TEXT,
    start_at        TIMESTAMPTZ  NOT NULL,
    end_at          TIMESTAMPTZ,
    frequency       VARCHAR(10)  NOT NULL,
    repeat_interval INTEGER      NOT NULL DEFAULT 1,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_rec_user      FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_wallet    FOREIGN KEY (wallet_id)   REFERENCES wallets    (id),
    CONSTRAINT fk_rec_category  FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_rec_type      CHECK (type IN ('EXPENSE', 'INCOME', 'LOAN')),
    CONSTRAINT ck_rec_amount    CHECK (amount > 0),
    CONSTRAINT ck_rec_frequency CHECK (frequency IN ('HOUR', 'DAY', 'WEEK', 'MONTH', 'QUARTER', 'YEAR')),
    CONSTRAINT ck_rec_interval  CHECK (repeat_interval >= 1),
    CONSTRAINT ck_rec_end_at    CHECK (end_at IS NULL OR end_at >= start_at)
);

COMMENT ON COLUMN recurring_transactions.end_at IS 'NULL = lặp vô hạn';

CREATE INDEX idx_rec_user   ON recurring_transactions (user_id) WHERE NOT is_deleted;
CREATE INDEX idx_rec_wallet ON recurring_transactions (wallet_id) WHERE NOT is_deleted;

-- recurring_transaction_schedules: từng buổi phát sinh của giao dịch định kỳ (entity RecTxnSchedule) --
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

    CONSTRAINT fk_rec_sched_rec    FOREIGN KEY (recurring_id) REFERENCES recurring_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_sched_user   FOREIGN KEY (user_id)      REFERENCES users                  (id) ON DELETE CASCADE,
    CONSTRAINT ck_rec_sched_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'SKIPPED')),
    CONSTRAINT ck_rec_sched_amount CHECK (amount > 0)
);

CREATE INDEX idx_rec_sched_rec_id      ON recurring_transaction_schedules (recurring_id);
CREATE INDEX idx_rec_sched_user_run_at ON recurring_transaction_schedules (user_id, run_at);
-- scheduler quét các buổi PENDING đã tới giờ (RecTxnScheduleRepository.findByStatusAndRunAtLessThanEqualAndIsDeletedFalse)
CREATE INDEX idx_rec_sched_due         ON recurring_transaction_schedules (run_at) WHERE status = 'PENDING' AND NOT is_deleted;

-- reconcil_history: ví bị lệch số dư mà job đối chiếu đã sửa (do WalletRepository.reconcileAll ghi) --
CREATE TABLE reconcil_history (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id         UUID        NOT NULL,
    wallet_balance    BIGINT      NOT NULL,
    total_transaction BIGINT      NOT NULL,
    is_deleted        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_rh_wallet FOREIGN KEY (wallet_id) REFERENCES wallets (id) ON DELETE CASCADE
);

COMMENT ON COLUMN reconcil_history.wallet_balance    IS 'Số dư ví trước khi đối chiếu';
COMMENT ON COLUMN reconcil_history.total_transaction IS 'Số dư đúng, bằng tổng giao dịch chưa xoá của ví';

CREATE INDEX idx_rh_wallet_created ON reconcil_history (wallet_id, created_at DESC);
