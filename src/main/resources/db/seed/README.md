# Hướng Dẫn Nạp Dữ Liệu Mẫu (Seed Data) — Schema migration_v2

Thư mục này chứa bộ file SQL khởi tạo dữ liệu mô phỏng quy mô lớn (High Volume / Large Scale) cho hệ thống, tương thích hoàn toàn với schema **`migration_v2`**.

---

## Danh Mục Các File Seed

Bộ dữ liệu được chia nhỏ thành các file độc lập theo thứ tự thực thi rõ ràng:

| Thứ tự | Tên File | Mô Tả |
| :---: | :--- | :--- |
| **0** | `reset_db.sql` | **Dọn sạch dữ liệu seed** (giữ lại admin, roles, permissions, categories, icons). |
| **1** | `01_seed_users_and_auth.sql` | Nạp **100.000 User** mẫu, gán quyền `ROLE_USER`, 10.000 refresh token, login attempts. *(Trigger V6 tự sinh 100.000 ví mặc định)*. |
| **2** | `02_seed_wallets_and_personal_txns.sql` | Tạo thêm ví Ngân hàng, ví MoMo và nạp **giao dịch cá nhân** (lương tháng + chi tiêu hàng ngày). Tự động đồng bộ số dư ví. |
| **3** | `03_seed_recurring_transactions.sql` | Tạo các **gói chi tiêu định kỳ** (Netflix, Internet, Gym, Tiền nhà) & các buổi lịch chạy (`SUCCESS` và `PENDING`). |
| **4** | `04_seed_groups_and_members.sql` | Tạo **1 Nhóm Siêu Lớn** (100 thành viên) và **10.000 nhóm sinh hoạt** thông thường kèm quỹ nhóm và thành viên. |
| **5** | `05_seed_group_transactions.sql` | Stored Procedure nạp giao dịch nhóm cực lớn (mặc định 100.000 giao dịch có `COMMIT` theo đợt) và phân bổ chi phí. |
| **6** | `06_rebuild_balances_and_analyze.sql` | Tính toán lại số dư thành viên (`fn_rebuild_group_member_balances`), số dư quỹ nhóm, số dư ví và chạy `VACUUM ANALYZE`. |

---

## Hướng Dẫn Thực Thi

### Cách 1: Chạy qua DataGrip / DBeaver / Navicat
Mở lần lượt từng file từ `01` đến `06` và nhấn **Execute (Ctrl+Enter / Cmd+Enter)**.

### Cách 2: Chạy qua dòng lệnh `psql`
```bash
# 1. Reset dữ liệu cũ nếu cần
psql -U postgres -d finance -f src/main/resources/db/seed/reset_db.sql

# 2. Chạy lần lượt các phần seed
psql -U postgres -d finance -f src/main/resources/db/seed/01_seed_users_and_auth.sql
psql -U postgres -d finance -f src/main/resources/db/seed/02_seed_wallets_and_personal_txns.sql
psql -U postgres -d finance -f src/main/resources/db/seed/03_seed_recurring_transactions.sql
psql -U postgres -d finance -f src/main/resources/db/seed/04_seed_groups_and_members.sql
psql -U postgres -d finance -f src/main/resources/db/seed/05_seed_group_transactions.sql
psql -U postgres -d finance -f src/main/resources/db/seed/06_rebuild_balances_and_analyze.sql
```

---

## Điểm Khác Biệt Quan Trọng So Với Schema Cũ

1. **`users`**: Không còn các cột cũ (`role`, `is_confirm`, `is_blocked`). Sử dụng `status` (`active`, `pending_verify`, `blocked`) và quan hệ qua bảng `user_roles`.
2. **Trigger ví mặc định (`V6`)**: Khi INSERT `users`, trigger `trg_users_default_wallet` tự động tạo 1 ví mặc định `Tiền mặt` (`CASH`).
3. **Cây danh mục (`categories`)**: Không có cột `type`. Toàn bộ giao dịch gắn vào các danh mục lá (`is_leaf = true`). Giao dịch cá nhân dùng `category_code` (VARCHAR), giao dịch nhóm dùng `category_id` (UUID).
4. **Trigger số dư ví (`V7`)**: Giao dịch tự động cập nhật số dư ví cá nhân theo hàm `fn_txn_wallet_effect`.
5. **Số dư thành viên nhóm**: Bảng `group_member_balances` đã bỏ `paid_out_of_pocket` và gộp vào `contribution` (chuẩn hóa theo logic mới).
