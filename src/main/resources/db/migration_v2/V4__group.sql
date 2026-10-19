-- =============================================================
-- V4 — Nhóm chung quỹ (Group)
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
    is_deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
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
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id   UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at  TIMESTAMPTZ,
    left_at    TIMESTAMPTZ,
    is_deleted BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

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

CREATE INDEX idx_gm_group ON group_members (group_id) WHERE NOT is_deleted;
CREATE INDEX idx_gm_user  ON group_members (user_id)  WHERE NOT is_deleted;

-- Bảng group_funds — Quỹ nhóm duy nhất (Gắn với thủ quỹ)
CREATE TABLE group_funds (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID        NOT NULL,
    keepper_id      UUID        NOT NULL,
    current_balance BIGINT      NOT NULL DEFAULT 0,
    is_deleted      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_gf_group   FOREIGN KEY (group_id)   REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gf_keepper FOREIGN KEY (keepper_id) REFERENCES users  (id) ON DELETE RESTRICT,
    CONSTRAINT uq_group_funds_group UNIQUE (group_id)
);

-- Bảng group_transactions — Giao dịch tài chính của nhóm
CREATE TABLE group_transactions (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id      UUID         NOT NULL,
    money_source  VARCHAR(20)  NOT NULL,
    transactor_id UUID,
    created_by    UUID         NOT NULL,
    category_code varchar(50),
    type          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    reviewed_by   UUID,
    reviewed_at   TIMESTAMPTZ,
    amount        BIGINT       NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    note          VARCHAR(255),
    deleted_at    TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_gt_group      FOREIGN KEY (group_id)      REFERENCES groups     (id) ON DELETE CASCADE,
    CONSTRAINT fk_gt_transactor FOREIGN KEY (transactor_id) REFERENCES users      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_creator    FOREIGN KEY (created_by)    REFERENCES users      (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gt_category   FOREIGN KEY (category_code)   REFERENCES categories (code) ON DELETE RESTRICT,
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
        (type = 'EXPENSE' AND category_code IS NOT NULL)
     OR (type = 'CONTRIBUTION' AND money_source = 'PERSONAL' AND category_code IS NULL)
     OR (type IN ('REFUND', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN') AND money_source = 'FUND' AND category_id IS NULL)
    ),
    CONSTRAINT ck_gt_transactor CHECK (
        (money_source = 'PERSONAL' AND transactor_id IS NOT NULL)
     OR (money_source = 'FUND' AND transactor_id IS NULL)
    )
);

CREATE INDEX idx_gt_group_occurred ON group_transactions (group_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_gt_transactor     ON group_transactions (transactor_id)              WHERE deleted_at IS NULL;
CREATE INDEX idx_gt_category       ON group_transactions (category_code)                WHERE deleted_at IS NULL;

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

CREATE INDEX idx_gtp_user ON group_transaction_participants (user_id);

-- Bảng group_member_balances — Số dư tích luỹ của từng thành viên
CREATE TABLE group_member_balances (
    group_id     UUID   NOT NULL,
    user_id      UUID   NOT NULL,
    contribution BIGINT NOT NULL DEFAULT 0,
    refund       BIGINT NOT NULL DEFAULT 0,
    share        BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (group_id, user_id),
    CONSTRAINT fk_gmb_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_gmb_user  FOREIGN KEY (user_id)  REFERENCES users  (id) ON DELETE CASCADE
);

-- Hàm tính lại toàn bộ bảng group_member_balances từ lịch sử giao dịch nhóm
CREATE OR REPLACE FUNCTION fn_rebuild_group_member_balances() RETURNS void
    LANGUAGE plpgsql AS
$$
BEGIN
    DELETE FROM group_member_balances;

    INSERT INTO group_member_balances (group_id, user_id, contribution, refund, share)
    SELECT group_id,
           user_id,
           SUM(contribution),
           SUM(refund),
           SUM(share)
    FROM (
        SELECT group_id, transactor_id AS user_id, amount AS contribution, 0 AS refund, 0 AS share
        FROM group_transactions
        WHERE status = 'CONFIRMED'
          AND deleted_at IS NULL
          AND (type = 'CONTRIBUTION'
               OR (type = 'EXPENSE' AND money_source = 'PERSONAL'))

        UNION ALL

        SELECT gt.group_id, p.user_id, 0, p.share_amount, 0
        FROM group_transaction_participants p
        JOIN group_transactions gt ON gt.id = p.group_transaction_id
        WHERE gt.status = 'CONFIRMED'
          AND gt.deleted_at IS NULL
          AND gt.type = 'REFUND'

        UNION ALL

        SELECT gt.group_id,
               p.user_id,
               0,
               0,
               CASE WHEN gt.type = 'ADJUSTMENT_UP' THEN -p.share_amount ELSE p.share_amount END
        FROM group_transaction_participants p
        JOIN group_transactions gt ON gt.id = p.group_transaction_id
        WHERE gt.status = 'CONFIRMED'
          AND gt.deleted_at IS NULL
          AND gt.type IN ('EXPENSE', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN')
    ) combined
    GROUP BY group_id, user_id;
END;
$$;
