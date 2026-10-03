-- =============================================================
-- V15: thêm cột email cho refresh_tokens
-- Entity RefreshToken và RefreshTokenRepository (revokeAllActiveForUser theo email,
-- findAllByEmailAndRevokedAtIsNull) đã dùng cột này nhưng V1 chưa tạo, nên mọi truy vấn
-- trên bảng refresh_tokens lỗi "column email does not exist".
-- =============================================================

ALTER TABLE refresh_tokens ADD COLUMN email VARCHAR(255);

-- bản ghi cũ lấy email từ chủ token
UPDATE refresh_tokens rt
SET email = u.email
FROM users u
WHERE u.id = rt.user_id;

ALTER TABLE refresh_tokens ALTER COLUMN email SET NOT NULL;

COMMENT ON COLUMN refresh_tokens.email IS 'Email chủ token, để thu hồi mọi phiên theo email (đặt lại mật khẩu)';

-- phục vụ thu hồi mọi phiên còn hiệu lực theo email
CREATE INDEX idx_rt_email ON refresh_tokens (email) WHERE revoked_at IS NULL;
