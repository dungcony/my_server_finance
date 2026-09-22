-- =============================================================
-- V10 — Phân hệ Nhóm chung quỹ (Group)
-- Mô hình hai sổ ghi tách biệt (Two Separate Ledgers)
-- =============================================================

-- -------------------------------------------------------------
-- 1. groups — Thông tin nhóm chung
-- -------------------------------------------------------------
CREATE TABLE groups (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                    VARCHAR(100) NOT NULL,
    description             VARCHAR(255),
    invite_code             VARCHAR(32)  NOT NULL,
    invite_code_expires_at  TIMESTAMPTZ  NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    target                  BIGINT,
    is_settlement_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    is_join_without_confirm BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_groups_invite CHECK (invite_code <> ''),
    CONSTRAINT ck_groups_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
    CONSTRAINT ck_groups_target CHECK (target IS NULL OR (target > 0 AND target <= 999999999999))
);

CREATE UNIQUE INDEX uq_groups_invite_code ON groups (invite_code) WHERE status <> 'DELETED';
CREATE INDEX idx_groups_status ON groups (status);

COMMENT ON TABLE groups IS 'Nhóm chung chia sẻ quỹ chi tiêu theo mô hình sổ nhóm độc lập';
COMMENT ON COLUMN groups.is_settlement_enabled IS 'TRUE = tính phần thừa thiếu của từng người và phân chia công nợ; FALSE = chỉ ghi chép';
COMMENT ON COLUMN groups.is_join_without_confirm IS 'TRUE = nhập mã vào thẳng; FALSE = chờ duyệt (PENDING)';

-- -------------------------------------------------------------
-- 2. group_members — Thành viên nhóm và lịch sử tham gia
-- -------------------------------------------------------------
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

CREATE UNIQUE INDEX uq_group_member_current
    ON group_members (group_id, user_id)
    WHERE status IN ('PENDING', 'ACTIVE');

CREATE INDEX idx_gm_group ON group_members (group_id);
CREATE INDEX idx_gm_user  ON group_members (user_id);
CREATE INDEX idx_gm_group_user_active ON group_members (group_id, user_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE group_members IS 'Thành viên nhóm; lưu khoảng thời gian để xác định thành viên có mặt vào thời điểm giao dịch';
COMMENT ON COLUMN group_members.joined_at IS 'NULL khi còn PENDING; có giá trị từ lúc được duyệt vào nhóm';
COMMENT ON COLUMN group_members.left_at   IS 'NULL = vẫn đang ở trong nhóm';

-- -------------------------------------------------------------
-- 3. group_wallets — Quỹ nhóm duy nhất (Gắn với thủ quỹ)
-- -------------------------------------------------------------
CREATE TABLE group_wallets (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id         UUID        NOT NULL,
    held_by_user_id  UUID        NOT NULL,
    current_balance  BIGINT      NOT NULL DEFAULT 0,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_gw_group   FOREIGN KEY (group_id)        REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gw_held_by FOREIGN KEY (held_by_user_id) REFERENCES users  (id) ON DELETE RESTRICT,
    CONSTRAINT uq_group_wallets_group UNIQUE (group_id),
    CONSTRAINT ck_gw_status  CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_gw_held_by ON group_wallets (held_by_user_id);

COMMENT ON TABLE group_wallets IS 'Quỹ duy nhất của nhóm, giao cho một thủ quỹ cầm giữ';
COMMENT ON COLUMN group_wallets.current_balance IS 'Số dư quỹ, được phép âm khi nhóm chi vượt quỹ';

-- -------------------------------------------------------------
-- 4. group_transactions — Giao dịch tài chính của nhóm
-- -------------------------------------------------------------
CREATE TABLE group_transactions (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id                UUID         NOT NULL,
    money_source            VARCHAR(20)  NOT NULL,
    user_id                 UUID         NOT NULL,
    created_by              UUID         NOT NULL,
    category_id             UUID,
    type                    VARCHAR(20)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by             UUID,
    reviewed_at             TIMESTAMPTZ,
    amount                  BIGINT       NOT NULL,
    occurred_at             TIMESTAMPTZ  NOT NULL,
    note                    VARCHAR(255),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT fk_gt_group      FOREIGN KEY (group_id)    REFERENCES groups     (id) ON DELETE CASCADE,
    CONSTRAINT fk_gt_user       FOREIGN KEY (user_id)     REFERENCES users      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_creator    FOREIGN KEY (created_by)  REFERENCES users      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_category   FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_reviewer   FOREIGN KEY (reviewed_by) REFERENCES users      (id) ON DELETE RESTRICT,

    CONSTRAINT ck_gt_amount CHECK (amount > 0 AND amount <= 999999999999),
    CONSTRAINT ck_gt_type   CHECK (type IN ('EXPENSE', 'CONTRIBUTION', 'REFUND', 'WITHDRAWAL', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN')),
    CONSTRAINT ck_gt_money_source CHECK (money_source IN ('FUND', 'PERSONAL')),
    CONSTRAINT ck_gt_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_gt_review CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL     AND reviewed_at IS NULL)
     OR (status IN ('CONFIRMED', 'REJECTED') AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_gt_treasurer_confirmed CHECK (
        type NOT IN ('REFUND', 'WITHDRAWAL', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN') OR status = 'CONFIRMED'
    ),
    CONSTRAINT ck_gt_shape  CHECK (
        (type = 'EXPENSE' AND category_id IS NOT NULL)
     OR (type = 'CONTRIBUTION' AND money_source = 'PERSONAL' AND category_id IS NULL)
     OR (type IN ('REFUND', 'WITHDRAWAL', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN') AND money_source = 'FUND' AND category_id IS NULL)
    )
);

CREATE INDEX idx_gt_group_occurred ON group_transactions (group_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_gt_group_status   ON group_transactions (group_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_gt_user           ON group_transactions (user_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE group_transactions IS 'Giao dịch thu, chi, góp quỹ, kiểm kê của nhóm';
COMMENT ON COLUMN group_transactions.money_source IS 'FUND = tiền quỹ; PERSONAL = tiền bản thân';
COMMENT ON COLUMN group_transactions.user_id IS 'Người bỏ tiền ra (EXPENSE, CONTRIBUTION) hoặc người nhận tiền (REFUND, WITHDRAWAL) hoặc người kiểm kê (ADJUSTMENT_*)';
COMMENT ON COLUMN group_transactions.created_by IS 'Người bấm ghi, có thể khác user_id';
COMMENT ON COLUMN group_transactions.occurred_at IS 'Thời điểm phát sinh giao dịch thật sự (khác created_at khi ghi bù)';

-- -------------------------------------------------------------
-- 5. group_transaction_participants — Người cùng chia tiền
-- -------------------------------------------------------------
CREATE TABLE group_transaction_participants (
    group_transaction_id UUID   NOT NULL,
    user_id              UUID   NOT NULL,
    share_amount         BIGINT,

    PRIMARY KEY (group_transaction_id, user_id),
    CONSTRAINT fk_gtp_transaction FOREIGN KEY (group_transaction_id) REFERENCES group_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_gtp_user        FOREIGN KEY (user_id)              REFERENCES users              (id) ON DELETE CASCADE,
    CONSTRAINT ck_gtp_share_amount CHECK (share_amount IS NULL OR (share_amount >= 0 AND share_amount <= 999999999999))
);

CREATE INDEX idx_gtp_user ON group_transaction_participants (user_id);

COMMENT ON TABLE group_transaction_participants IS 'Người cùng chịu chi phí; vắng dòng nghĩa là chia đều cho mọi thành viên có mặt vào thời điểm giao dịch';
COMMENT ON COLUMN group_transaction_participants.share_amount IS 'NULL = chia đều trong số người được chọn; có số = chia theo chỉ định';
