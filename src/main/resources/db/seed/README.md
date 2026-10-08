# Hướng dẫn nạp dữ liệu mẫu (Seed Data)

Thư mục này chứa các file SQL phục vụ nạp và quản lý dữ liệu mẫu (stress test) cho PostgreSQL.

---

## 1. File tổng hợp gốc: `seed_user_auth_group.sql`

File chạy toàn bộ dữ liệu mẫu ban đầu:
- 100.000 User, roles, refresh tokens.
- 1 Nhóm Siêu Lớn (100 thành viên) và 10.000 Nhóm thông thường.
- Stored Procedure sinh 1.000.000 giao dịch (và 3.500.000 người chia tiền) có `COMMIT;` theo từng lô.
- Cập nhật lại số dư thành viên và quỹ nhóm.

> **Khuyên dùng**: Sử dụng công cụ **DataGrip** (hoặc `psql`) để thực thi file này đạt hiệu năng cao nhất và không bị xung đột transaction hay timeout.

---

## 2. Bộ 5 file lẻ chạy theo từng đợt

Dùng khi bạn muốn kiểm soát tiến độ hoặc nạp từng phần dữ liệu độc lập:

| Thứ tự | Tên file | Mô tả |
| :--- | :--- | :--- |
| 1 | `01_seed_users_and_auth.sql` | Nạp 100.000 User, gán quyền `ROLE_USER`, refresh tokens mẫu. |
| 2 | `02_seed_groups_and_members.sql` | Nạp 1 Nhóm Siêu Lớn và 10.000 Nhóm thông thường kèm thành viên và quỹ. |
| 3 | `03_create_seed_transactions_proc.sql` | Tạo Stored Procedure `seed_bulk_group_transactions`. |
| 4 | `04_execute_seed_transactions.sql` | Chạy thủ tục `CALL seed_bulk_group_transactions(1000000, 100000);`. |
| 5 | `05_rebuild_balances_and_funds.sql` | Đồng bộ lại bảng số dư thành viên và quỹ nhóm. |

---

## 3. File dọn dẹp dữ liệu: `reset_db.sql`

Sử dụng khi bạn muốn xóa sạch toàn bộ dữ liệu mẫu đã seed (bao gồm 1 triệu giao dịch, các nhóm và 100.000 user mẫu) để nạp lại từ đầu chỉ trong vài mili-giây:
- Bảo toàn an toàn tài khoản admin `admin@financeapp.com`.
- Bảo toàn danh mục hệ thống và bảng phân quyền.
