-- =============================================================
-- V5 — Trigger ví mặc định: tự tạo ví khi có user mới, ràng buộc mỗi user đúng một ví mặc định
-- =============================================================

-- -------------------------------------------------------------
-- Tạo user mới thì tự tạo ví tiền mặt mặc định
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_users_default_wallet()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO wallets (user_id, name, type, is_default, icon_id)
    VALUES (
        NEW.id,
        'Tiền mặt',
        'CASH',
        TRUE,
        (SELECT id FROM icons WHERE code = 'default_wallet')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_default_wallet
    AFTER INSERT ON users
    FOR EACH ROW EXECUTE FUNCTION fn_users_default_wallet();

-- -------------------------------------------------------------
-- Mỗi user luôn có đúng một ví mặc định chưa xoá.
-- Kiểm lúc commit (DEFERRABLE) để đổi ví mặc định A sang B trong cùng một transaction vẫn hợp lệ.
-- -------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_wallets_only_one_default()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT count(*)
        FROM wallets
        WHERE user_id = NEW.user_id
          AND is_default
          AND NOT is_deleted) <> 1 THEN
        RAISE EXCEPTION 'Mỗi người dùng phải có đúng một ví mặc định (user %)', NEW.user_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_wallets_only_one_default
    AFTER INSERT OR UPDATE OF is_default, is_deleted ON wallets
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_wallets_only_one_default();
