-- =============================================================
-- XÓA SẠCH DỮ LIỆU MẪU (RESET SEED DATA)
-- Dọn dẹp toàn bộ dữ liệu mẫu đã seed để có thể nạp lại từ đầu
-- Bảo toàn tài khoản admin@financeapp.com và các danh mục hệ thống
-- Hệ quản trị cơ sở dữ liệu: PostgreSQL
-- =============================================================

-- Dọn dẹp toàn bộ dữ liệu nhóm, quỹ nhóm, giao dịch và số dư phái sinh
TRUNCATE TABLE 
    group_member_balances,
    group_transaction_participants,
    group_transactions,
    group_funds,
    group_members,
    groups
CASCADE;

-- Xóa refresh tokens của các tài khoản mẫu
DELETE FROM refresh_tokens 
WHERE email LIKE 'user%@financeapp.com';

-- Xóa 100.000 tài khoản mẫu (ON DELETE CASCADE sẽ tự động dọn sạch user_roles liên quan, giữ lại admin)
DELETE FROM users 
WHERE email LIKE 'user%@financeapp.com';
