-- =============================================================
-- V11 — Bỏ thời hạn mã mời nhóm (invite_code_expires_at)
-- Có mã mời là được tham gia nhóm, mã không bao giờ tự hết hạn
-- =============================================================

ALTER TABLE groups DROP COLUMN IF EXISTS invite_code_expires_at;
