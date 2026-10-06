-- Chuyển đổi mã mời nhóm (invite_code) thành UNIQUE vô điều kiện toàn bảng

-- Xóa partial index có điều kiện cũ
DROP INDEX IF EXISTS uq_groups_invite_code;

-- Ràng buộc UNIQUE vô điều kiện trên cột invite_code
ALTER TABLE groups
    ADD CONSTRAINT uq_groups_invite_code UNIQUE (invite_code);
