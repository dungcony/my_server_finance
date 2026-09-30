-- =============================================================
-- V12 — Loại bỏ trường status khỏi group_funds
-- Quỹ đi theo vòng đời của nhóm nên không cần status riêng
-- =============================================================

ALTER TABLE group_funds DROP CONSTRAINT IF EXISTS ck_gf_status;
ALTER TABLE group_funds DROP COLUMN IF EXISTS status;
