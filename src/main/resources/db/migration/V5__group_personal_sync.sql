-- =============================================================
-- V5 — Ghi giao dịch tiền túi trong nhóm sang sổ cá nhân
-- Ví mặc định, dữ liệu phụ và loại thông báo, danh mục hệ thống mới
-- =============================================================

-- =============================================================
-- KHỐI VÍ MẶC ĐỊNH
-- =============================================================

ALTER TABLE wallets ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN wallets.is_default IS 'Ví mặc định của người dùng, nhận các khoản tự ghi từ giao dịch nhóm. Mỗi người dùng luôn có đúng một ví mặc định chưa xoá';

-- Người dùng cũ chưa có ví nào thì tạo ví tiền mặt mặc định
INSERT INTO wallets (user_id, name, type, is_default)
SELECT u.id, 'Tiền mặt', 'cash', TRUE
FROM users u
WHERE NOT EXISTS (SELECT 1 FROM wallets w WHERE w.user_id = u.id AND NOT w.is_deleted);

-- Người dùng đã có ví nhưng chưa có ví mặc định thì lấy ví tạo sớm nhất
UPDATE wallets w SET is_default = TRUE
WHERE w.id IN (
        SELECT DISTINCT ON (user_id) id
        FROM wallets
        WHERE NOT is_deleted
        ORDER BY user_id, created_at, id)
  AND NOT EXISTS (
        SELECT 1 FROM wallets d
        WHERE d.user_id = w.user_id AND d.is_default AND NOT d.is_deleted);

-- Tạo người dùng thì tạo luôn ví tiền mặt mặc định
CREATE OR REPLACE FUNCTION fn_users_default_wallet()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO wallets (user_id, name, type, is_default)
    VALUES (NEW.id, 'Tiền mặt', 'cash', TRUE);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_default_wallet
    AFTER INSERT ON users
    FOR EACH ROW EXECUTE FUNCTION fn_users_default_wallet();

-- Mỗi người dùng đúng một ví mặc định chưa xoá. Kiểm tra hoãn đến lúc commit để đổi ví
-- mặc định được bằng hai UPDATE (bỏ cờ cũ, đặt cờ mới) trong cùng một transaction
CREATE OR REPLACE FUNCTION fn_wallets_one_default()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT count(*) FROM wallets
        WHERE user_id = NEW.user_id AND is_default AND NOT is_deleted) <> 1 THEN
        RAISE EXCEPTION 'Mỗi người dùng phải có đúng một ví mặc định (user %)', NEW.user_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_wallets_one_default
    AFTER INSERT OR UPDATE OF is_default, is_deleted ON wallets
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_wallets_one_default();

-- =============================================================
-- KHỐI THÔNG BÁO
-- =============================================================

ALTER TABLE notifications ADD COLUMN data JSONB;

ALTER TABLE notifications DROP CONSTRAINT ck_notif_type;

-- type chuyển sang lưu tên hằng của enum NotiType (chữ hoa)
UPDATE notifications SET type = UPPER(type);

ALTER TABLE notifications ADD CONSTRAINT ck_notif_type CHECK (type IN (
    'BUDGET_ALERT', 'DEBT_REMINDER', 'RECURRING_GENERATED',
    'BUDGET_RENEWED', 'GOAL_COMPLETED',
    'GROUP_TXN_REVIEW_REQUEST', 'GROUP_TXN_RECORD_REQUEST'
));

-- điều kiện của index phụ thuộc giá trị type nên phải tạo lại theo giá trị chữ hoa
DROP INDEX uq_notif_budget_alert;

CREATE UNIQUE INDEX uq_notif_budget_alert
ON notifications (user_id, reference_id, ((created_at AT TIME ZONE 'UTC')::date))
WHERE type = 'BUDGET_ALERT' AND reference_id IS NOT NULL;

COMMENT ON COLUMN notifications.type IS 'Loại thông báo (tên hằng NotiType), app dựa vào đây để chọn icon và màn hình đích';
COMMENT ON COLUMN notifications.reference_id IS 'Id đối tượng chính thông báo nhắc tới (ngân sách, khoản nợ, giao dịch nhóm...), tuỳ type; không có khoá ngoại';
COMMENT ON COLUMN notifications.data IS 'Dữ liệu phụ để app điều hướng, json phẳng, null nếu không cần; không chứa trạng thái. VD thông báo giao dịch nhóm: {"action": "...", "group_id": "..."}';

-- =============================================================
-- DANH MỤC HỆ THỐNG CHO GIAO DỊCH SINH TỪ NHÓM
-- =============================================================

INSERT INTO categories (code, name, icon_id, color, level, path)
SELECT v.code, v.name, i.id, v.color, 0, v.code
FROM (VALUES
    ('gop_quy_nhom',        'Góp quỹ nhóm',           'gia_dinh', '#2f8272'),
    ('nhan_hoan_tien_nhom', 'Nhận hoàn tiền từ nhóm', 'vi_tien',  '#4a7f6b')
) AS v(code, name, icon_code, color)
JOIN icons i ON i.code = v.icon_code;
