-- =============================================================
-- V2 — Toàn bộ chỉ mục (Indexes)
-- =============================================================

-- Nhóm Người dùng, Vai trò & Hạ tầng xác thực
CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

CREATE INDEX idx_rt_active ON refresh_tokens (token_hash) WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_email ON refresh_tokens (email) WHERE revoked_at IS NULL;
CREATE INDEX idx_rt_cleanup ON refresh_tokens (expires_at);

CREATE INDEX idx_la_email ON login_attempts (email, attempted_at DESC) WHERE NOT succeeded;
CREATE INDEX idx_la_ip ON login_attempts (ip_address, attempted_at DESC) WHERE NOT succeeded;
CREATE INDEX idx_la_cleanup ON login_attempts (attempted_at);

CREATE INDEX idx_idem_cleanup ON idempotency_keys (created_at);

-- Nhóm Ví & Danh mục
CREATE UNIQUE INDEX uq_wallets_user_name ON wallets (user_id, lower(name)) WHERE user_id IS NOT NULL AND NOT is_deleted;
CREATE INDEX idx_wallets_user ON wallets (user_id, sort_order) WHERE NOT is_deleted;

CREATE INDEX idx_icons_group ON icons (icon_group, sort_order) WHERE is_active;

CREATE UNIQUE INDEX uq_cat_name ON categories (lower(name)) WHERE NOT deleted;
CREATE INDEX idx_cat_parent ON categories (parent_id) WHERE NOT deleted;

-- Nhóm Giao dịch, Giao dịch định kỳ & Bản nháp
CREATE INDEX idx_draft_user_status ON ai_drafts (user_id, status, created_at DESC);
CREATE INDEX idx_draft_cleanup ON ai_drafts (created_at) WHERE status = 'discarded';

CREATE INDEX idx_rec_due ON recurring_transactions (next_run_date) WHERE is_enabled;

CREATE INDEX idx_txn_user_date ON transactions (user_id, date DESC) WHERE NOT is_deleted;
CREATE INDEX idx_txn_wallet_date ON transactions (wallet_id, date) WHERE NOT is_deleted;
CREATE INDEX idx_txn_category_date ON transactions (category_id, date) WHERE NOT is_deleted AND category_id IS NOT NULL;
CREATE INDEX idx_txn_dest_wallet ON transactions (destination_wallet_id) WHERE destination_wallet_id IS NOT NULL AND NOT is_deleted;
CREATE UNIQUE INDEX uq_txn_recurring_date ON transactions (recurring_id, date) WHERE recurring_id IS NOT NULL AND NOT is_deleted;

-- Nhóm Ngân sách
CREATE INDEX idx_bud_user ON budgets (user_id, is_active);
CREATE INDEX idx_bud_renew ON budgets (end_date) WHERE auto_renew AND is_active;

-- Nhóm Sổ nợ
CREATE INDEX idx_debt_user ON debts (user_id, status, due_date) WHERE status = 'outstanding';
CREATE INDEX idx_debt_due ON debts (due_date) WHERE status = 'outstanding' AND due_date IS NOT NULL;
CREATE INDEX idx_dp_debt ON debt_payments (debt_id, paid_date);

-- Nhóm Mục tiêu tích luỹ
CREATE INDEX idx_goal_user ON savings_goals (user_id, status);
CREATE INDEX idx_gc_goal ON goal_contributions (goal_id, contributed_date);

-- Nhóm Thông báo & Báo cáo xuất dữ liệu
CREATE INDEX idx_notif_user ON notifications (user_id, is_read, created_at DESC);

CREATE UNIQUE INDEX uq_notif_budget_alert
ON notifications (user_id, reference_id, ((created_at AT TIME ZONE 'UTC')::date))
WHERE type = 'budget_alert' AND reference_id IS NOT NULL;

CREATE INDEX idx_export_user ON export_jobs (user_id, created_at DESC);
CREATE INDEX idx_export_expires ON export_jobs (expires_at) WHERE status = 'completed';

-- Nhóm Quỹ chung (Group)
CREATE INDEX idx_groups_status ON groups (status);

CREATE UNIQUE INDEX uq_group_member_current ON group_members (group_id, user_id) WHERE status IN ('PENDING', 'ACTIVE');
CREATE INDEX idx_gm_group ON group_members (group_id);
CREATE INDEX idx_gm_user ON group_members (user_id);
CREATE INDEX idx_gm_group_user_active ON group_members (group_id, user_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_gf_keepper_id ON group_funds (keepper_id);

CREATE INDEX idx_gt_group_occurred ON group_transactions (group_id, occurred_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_gt_group_status ON group_transactions (group_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_gt_transactor ON group_transactions (transactor_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_gtp_user ON group_transaction_participants (user_id);
