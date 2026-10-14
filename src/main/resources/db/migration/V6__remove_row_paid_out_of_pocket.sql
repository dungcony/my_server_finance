-- =============================================================
-- V7 — Tạo biểu tượng mặc định cho ví & cập nhật ví mặc định
-- =============================================================

-- Thêm biểu tượng mặc định cho ví tài chính
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

-- Cập nhật ví mặc định đã tạo trước đó chưa có icon
UPDATE wallets
SET icon = 'default_wallet'
WHERE is_default AND (icon IS NULL OR icon = '');

-- Cập nhật hàm trigger tạo ví mặc định kèm icon mặc định khi có user mới
CREATE OR REPLACE FUNCTION fn_users_default_wallet()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO wallets (user_id, name, type, is_default, icon)
    VALUES (NEW.id, 'Tiền mặt', 'cash', TRUE, 'default_wallet');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
