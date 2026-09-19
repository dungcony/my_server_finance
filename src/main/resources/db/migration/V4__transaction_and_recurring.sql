-- =============================================================
-- V4 — Module Giao dịch & Giao dịch định kỳ (Transaction & Recurring)
-- =============================================================

-- -------------------------------------------------------------
-- 1. ai_drafts — Bản nháp giao dịch do AI phân tích
-- -------------------------------------------------------------
CREATE TABLE ai_drafts (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL,
    method           VARCHAR(20)  NOT NULL,
    raw_input        TEXT         NOT NULL,
    ai_result        JSONB        NOT NULL,
    confidence       NUMERIC(4,3) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'pending',
    transaction_id   UUID,
    user_corrections JSONB,
    model_version    VARCHAR(50),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_draft_user   FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_draft_method CHECK (method IN ('text', 'ocr')),
    CONSTRAINT ck_draft_status CHECK (status IN ('pending', 'saved', 'discarded')),
    CONSTRAINT ck_draft_conf   CHECK (confidence >= 0 AND confidence <= 1)
);

CREATE INDEX idx_draft_user_status ON ai_drafts (user_id, status, created_at DESC);
CREATE INDEX idx_draft_cleanup     ON ai_drafts (created_at) WHERE status = 'discarded';

-- -------------------------------------------------------------
-- 2. recurring_transactions — Khoản thu/chi lặp lại định kỳ
-- -------------------------------------------------------------
CREATE TABLE recurring_transactions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL,
    wallet_id     UUID         NOT NULL,
    category_id   UUID         NOT NULL,
    type          VARCHAR(10)  NOT NULL,
    amount        BIGINT       NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    note          TEXT,
    frequency     VARCHAR(10)  NOT NULL,
    interval      INTEGER      NOT NULL DEFAULT 1,
    start_date    DATE         NOT NULL,
    end_date      DATE,
    next_run_date DATE         NOT NULL,
    last_run_date DATE,
    is_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_rec_user     FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE CASCADE,
    CONSTRAINT fk_rec_wallet   FOREIGN KEY (wallet_id)   REFERENCES wallets    (id) ON DELETE RESTRICT,
    CONSTRAINT fk_rec_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT ck_rec_type      CHECK (type IN ('expense', 'income')),
    CONSTRAINT ck_rec_amount    CHECK (amount > 0),
    CONSTRAINT ck_rec_frequency CHECK (frequency IN ('day', 'week', 'month', 'year')),
    CONSTRAINT ck_rec_interval  CHECK (interval >= 1),
    CONSTRAINT ck_rec_end_date  CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_rec_due ON recurring_transactions (next_run_date) WHERE is_enabled;

-- -------------------------------------------------------------
-- 3. transactions — Bảng giao dịch tài chính (Trung tâm)
-- -------------------------------------------------------------
CREATE TABLE transactions (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL,
    wallet_id             UUID         NOT NULL,
    destination_wallet_id UUID,
    category_id           UUID,
    type                  VARCHAR(10)  NOT NULL,
    amount                BIGINT       NOT NULL,
    date                  DATE         NOT NULL,
    note                  TEXT,
    display_name          VARCHAR(150),
    source                VARCHAR(20)  NOT NULL DEFAULT 'manual',
    recurring_id          UUID,
    draft_id              UUID,
    receipt_url           TEXT,
    is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    counts_in_report      BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_txn_user      FOREIGN KEY (user_id)               REFERENCES users                  (id) ON DELETE CASCADE,
    CONSTRAINT fk_txn_wallet    FOREIGN KEY (wallet_id)             REFERENCES wallets                (id) ON DELETE RESTRICT,
    CONSTRAINT fk_txn_dest      FOREIGN KEY (destination_wallet_id) REFERENCES wallets                (id) ON DELETE RESTRICT,
    CONSTRAINT fk_txn_category  FOREIGN KEY (category_id)           REFERENCES categories             (id) ON DELETE RESTRICT,
    CONSTRAINT fk_txn_recurring FOREIGN KEY (recurring_id)          REFERENCES recurring_transactions (id) ON DELETE SET NULL,
    CONSTRAINT fk_txn_draft     FOREIGN KEY (draft_id)              REFERENCES ai_drafts              (id) ON DELETE SET NULL,

    CONSTRAINT ck_txn_type   CHECK (type   IN ('expense', 'income', 'transfer')),
    CONSTRAINT ck_txn_source CHECK (source IN ('manual', 'text', 'ocr', 'auto', 'adjustment')),
    CONSTRAINT ck_txn_amount CHECK (amount > 0),
    CONSTRAINT ck_txn_shape  CHECK (
        (type = 'transfer'
            AND destination_wallet_id IS NOT NULL
            AND destination_wallet_id <> wallet_id
            AND category_id IS NULL)
        OR
        (type IN ('expense', 'income')
            AND destination_wallet_id IS NULL
            AND category_id IS NOT NULL)
    )
);

COMMENT ON TABLE  transactions                  IS 'Giao dịch thu, chi, chuyển tiền';
COMMENT ON COLUMN transactions.counts_in_report IS 'FALSE cho giao dịch điều chỉnh kiểm kê ví không tính vào báo cáo';

CREATE INDEX idx_txn_user_date     ON transactions (user_id, date DESC)     WHERE NOT is_deleted;
CREATE INDEX idx_txn_wallet_date   ON transactions (wallet_id, date)        WHERE NOT is_deleted;
CREATE INDEX idx_txn_category_date ON transactions (category_id, date)      WHERE NOT is_deleted AND category_id IS NOT NULL;
CREATE INDEX idx_txn_dest_wallet   ON transactions (destination_wallet_id)  WHERE destination_wallet_id IS NOT NULL AND NOT is_deleted;

CREATE UNIQUE INDEX uq_txn_recurring_date
    ON transactions (recurring_id, date)
    WHERE recurring_id IS NOT NULL AND NOT is_deleted;

-- -------------------------------------------------------------
-- Triggers & Hàm dùng chung
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_transactions_validate()
RETURNS TRIGGER AS $$
DECLARE
    v_cat_type VARCHAR(10);
BEGIN
    IF NEW.category_id IS NOT NULL THEN
        SELECT type INTO v_cat_type FROM categories WHERE id = NEW.category_id;
        IF v_cat_type IS DISTINCT FROM NEW.type THEN
            RAISE EXCEPTION 'Loại danh mục không khớp loại giao dịch (CATEGORY_TYPE_MISMATCH)'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;

    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_transactions_validate
    BEFORE INSERT OR UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION fn_transactions_validate();

-- Hàm lấy cây danh mục cha kèm các con (đã lọc xoá mềm)
CREATE OR REPLACE FUNCTION fn_category_tree(p_category_id UUID)
RETURNS TABLE (category_id UUID) AS $$
    SELECT id FROM categories WHERE id = p_category_id AND NOT is_deleted
    UNION
    SELECT id FROM categories WHERE parent_category_id = p_category_id AND NOT is_deleted;
$$ LANGUAGE sql STABLE;

COMMENT ON FUNCTION fn_category_tree IS
    'Trả về danh mục cha kèm mọi con còn hoạt động. Dùng cho lọc giao dịch, tính ngân sách và báo cáo.';
