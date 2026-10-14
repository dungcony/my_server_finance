-- =============================================================
-- V7 — Tạo biểu tượng mặc định & cơ chế ví mặc định cho người dùng
-- =============================================================

-- 1. Tạo icon mặc định trong bảng icons
INSERT INTO icons (id, code, display_name, path_data, icon_group, search_keywords, sort_order, is_active)
VALUES (
    gen_random_uuid(),
    'default_wallet',
    'Ví mặc định',
    'M3 7a2 2 0 012-2h13v3M3 7v11a2 2 0 002 2h14a1 1 0 001-1v-3M18 11h4v4h-4a2 2 0 010-4z',
    'tai_chinh',
    'ví mặc định tiền mặt ví tiền default wallet cash',
    0,
    TRUE
)
ON CONFLICT (code) DO NOTHING;

-- 2. Cập nhật icon cho các ví mặc định hiện có (nếu có)
UPDATE wallets
SET icon = 'default_wallet'
WHERE is_default AND (icon IS NULL OR icon = '');

-- 3. Trigger 1: Tự động tạo ví tiền mặt mặc định khi có người dùng mới
CREATE OR REPLACE FUNCTION fn_users_default_wallet()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO wallets (user_id, name, type, is_default, icon)
    VALUES (NEW.id, 'Tiền mặt', 'cash', TRUE, 'default_wallet');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_default_wallet ON users;
CREATE TRIGGER trg_users_default_wallet
    AFTER INSERT ON users
    FOR EACH ROW EXECUTE FUNCTION fn_users_default_wallet();

-- 4. Trigger 2: Ràng buộc mỗi người dùng luôn có ĐÚNG 1 ví mặc định chưa xóa (kiểm tra deferred lúc commit)
CREATE OR REPLACE FUNCTION fn_wallets_only_one_default()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT count(*) FROM wallets
        WHERE user_id = NEW.user_id AND is_default AND NOT is_deleted) <> 1 THEN
        RAISE EXCEPTION 'Mỗi người dùng phải có đúng một ví mặc định (user %)', NEW.user_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_wallets_only_one_default ON wallets;
CREATE CONSTRAINT TRIGGER trg_wallets_only_one_default
    AFTER INSERT OR UPDATE OF is_default, is_deleted ON wallets
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_wallets_only_one_default();