-- Khoá lạc quan cho giao dịch nhóm: chặn hai request cùng duyệt/sửa một giao dịch
-- (quỹ bị cộng trừ hai lần). Hibernate tăng cột này mỗi lần ghi và chỉ ghi khi số còn khớp.
ALTER TABLE group_transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN group_transactions.version IS 'Khoá lạc quan: tăng 1 mỗi lần ghi, dùng để từ chối ghi đè khi bản ghi đã bị request khác đổi trước';
