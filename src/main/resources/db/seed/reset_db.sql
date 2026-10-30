-- =============================================================
-- XÓA SẠCH DỮ LIỆU MẪU (RESET SEED DATA)
-- Dọn dẹp toàn bộ dữ liệu mẫu đã seed để nạp lại từ đầu chỉ trong vài mili-giây
-- Bảo toàn an toàn:
--   - Tài khoản quản trị viên: admin@financeapp.com
--   - Bảng phân quyền: roles, permissions, role_permissions, user_roles của admin
--   - Kho biểu tượng và cây danh mục hệ thống: icons, categories
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

-- Lưu ý: Nếu muốn xóa triệt để toàn bộ schema để Flyway chạy lại từ V1:
-- DROP SCHEMA public CASCADE;
-- CREATE SCHEMA public;

-- Dọn dẹp toàn bộ bảng dữ liệu nhóm và giao dịch nhóm
TRUNCATE TABLE
    group_member_balances,
    group_transaction_participants,
    group_transactions,
    group_funds,
    group_members,
    groups,
    reconcil_history,
    recurring_transaction_schedules,
    recurring_transactions,
    transactions,
    notifications
CASCADE;

-- Xóa ví của các tài khoản mẫu (giữ lại ví mặc định của admin)
DELETE FROM wallets
WHERE user_id NOT IN (
    SELECT id FROM users WHERE email = 'admin@financeapp.com'
);

-- Reset số dư ví mặc định của admin về 0
UPDATE wallets
SET balance = 0
WHERE user_id IN (
    SELECT id FROM users WHERE email = 'admin@financeapp.com'
);

-- Xóa phiên đăng nhập và lịch sử đăng nhập của tài khoản mẫu
DELETE FROM refresh_tokens WHERE email <> 'admin@financeapp.com';
DELETE FROM login_attempts WHERE email <> 'admin@financeapp.com';
DELETE FROM idempotency_keys WHERE user_id NOT IN (
    SELECT id FROM users WHERE email = 'admin@financeapp.com'
);

-- Xóa 100.000 tài khoản người dùng mẫu (ON DELETE CASCADE tự dọn user_roles)
DELETE FROM users WHERE email <> 'admin@financeapp.com';
