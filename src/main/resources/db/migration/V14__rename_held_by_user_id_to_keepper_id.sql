-- =============================================================
-- V14 — Đổi tên cột held_by_user_id thành keepper_id
-- Đồng bộ tên gọi người giữ quỹ nhóm
-- =============================================================

ALTER TABLE group_funds RENAME COLUMN held_by_user_id TO keepper_id;
ALTER INDEX IF EXISTS idx_gf_held_by RENAME TO idx_gf_keepper_id;
