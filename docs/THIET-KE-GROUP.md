# GROUP
## PHA PHÂN TÍCH

### Thực thể
1. Nhóm chung       : nhóm chứa người và ví
   → tbl_group: name/description/status/target/is_split_equally/is_join_with_not_confirm/created
2. Ví               : nguồn tiền của nhóm hoặc của riêng user, để xác định số tiền còn lại
   -> tbl_wallets: name/type/cur_balance/status/created
3. Người dùng       : người dùng bình thường
   -> tbl_user: f_name/l_name/status/created
4. Danh mục thu chi : của hệ thống hoặc do user tự tạo, phân cấp
   -> tbl_categories: name/type/icon/description/status/is_leaf/level/path/created
5. Giao dịch        : một lần tiền vào/ra
   -> tbl_transactions: note/amount/type/date/created
6. Người trong nhóm : tư cách của một user trong một nhóm
   -> tbl_user_group: role/status/joined_at/left_at

### Quan hệ
- Người dùng  (1)    — (0..*) Người trong nhóm
- Nhóm        (1)    — (1..*) Người trong nhóm
- Nhóm        (0..1) — (0..*) Ví            : ví nhóm
- Người dùng  (0..1) — (0..*) Ví            : ví cá nhân (ví thuộc ĐÚNG MỘT trong hai)
- Người dùng  (0..1) — (0..*) Danh mục      : tự tạo (không có user = hệ thống)
- Danh mục    (0..1) — (0..*) Danh mục      : cha - con
- Người dùng  (1)    — (0..*) Giao dịch     : người ghi / người trả
- Ví          (0..1) — (0..*) Giao dịch
- Danh mục    (0..1) — (0..*) Giao dịch
