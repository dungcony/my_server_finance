# Review logic code — Tổng quan

- Ngày review: 2026-10-16 (Cập nhật: 2026-10-19)
- Nhánh: `check-db`
- Phạm vi còn tồn đọng: `transaction` (liên quan debt), `notification`
- Đã hoàn thành: `auth`, `user`, `wallet`, `category`, `icon`, `group`, `recurring`
- Bỏ qua: `debt`, `goal`, `budget`, `report` (chưa đụng tới / có kế hoạch riêng)
- Giữ nguyên: các trigger trong migration → **code phải chạy đúng theo trigger**

## Cách đọc

Mỗi lỗi có một mã (VD: `T6`, `N1`) và mức độ:

| Mức độ   | Nghĩa là                                                                    |
|----------|-----------------------------------------------------------------------------|
| **Nặng** | Gọi API là lỗi, hoặc làm sai tiền, hoặc xem/sửa được dữ liệu của người khác |
| **Vừa**  | Sai nghiệp vụ trong một số trường hợp                                       |
| **Nhẹ**  | Lỗi nhỏ, khó gặp, hoặc chỉ ảnh hưởng hiển thị                               |

## Các trigger được giữ nguyên

| Trigger                                | Làm gì                                                                                                            | Code cần lưu ý                                                          |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `trg_users_default_wallet`             | Tạo user mới → tự tạo ví "Tiền mặt" với `type = 'CASH'`, `icon_code = 'default_wallet'`, `is_default = true`      | Không đổi mã của icon `default_wallet`, trigger viết cứng mã này        |
| `trg_wallets_only_one_default`         | Mỗi user luôn có đúng 1 ví mặc định chưa xoá                                                                      | Không được xoá ví mặc định (code đã chặn)                               |
| `trg_wallets_initial_transaction`      | Tạo ví có `balance > 0` → tự sinh giao dịch INCOME "Số dư ban đầu", **không có danh mục**                         | Giao dịch không có danh mục vẫn phải xem/sửa được (code đã xử lý)       |
| `trg_transactions_wallet_balance`      | Giao dịch tạo/sửa/xoá → cộng trừ `wallets.balance` (INCOME cộng, EXPENSE và LOAN trừ)                             | Java không tự cộng trừ số dư                                            |
| `trg_wallets_soft_delete_cascade`      | Xoá mềm ví → xoá mềm giao dịch của ví, tắt định kỳ, bỏ các buổi lịch chưa chạy                                    | Không cần xoá giao dịch bằng Java khi xoá ví                            |
| `trg_transactions_category_leaf`       | Gắn hoặc đổi danh mục giao dịch → danh mục phải là lá, chưa xoá                                                   | Lỗi trả qua `CONSTRAINT_MAP` (`TriggerProperty`)                        |
| `trg_transactions_block_debt_delete`   | Chặn xoá mềm giao dịch đang gắn với khoản nợ                                                                      | Mã lỗi chưa có trong `CONSTRAINT_MAP` (xem `T6`)                        |
| `trg_<bảng>_updated_at`                | Tự cập nhật `updated_at` khi sửa, gắn cho mọi bảng có cột này                                                     | Không có vấn đề                                                         |

Số dư ví do trigger `trg_transactions_wallet_balance` lo; job đối chiếu hằng đêm chỉ để sửa các ví bị lệch.

## Quy ước đã chốt

- Enum trong JSON viết hoa đúng tên enum (VD `CASH`, `EXPENSE`), không nhận chữ thường.

## Danh sách lỗi chưa sửa

| Mã  | Mức  | Lỗi                                                                  | Chi tiết                                               |
|-----|------|----------------------------------------------------------------------|--------------------------------------------------------|
| T6  | Vừa  | Chưa chặn sửa giao dịch gốc của khoản nợ                             | [transaction](review-03-transaction.md#t6)             |
| N1  | Nặng | Danh sách thông báo luôn rỗng                                        | [notification](review-07-notification.md#n1)           |
| N2  | Vừa  | Thông báo giao dịch nhóm gửi lỗi / sai nội dung                      | [notification](review-07-notification.md#n2)           |
| N3  | Nhẹ  | API danh sách thông báo không bọc `ApiResponse`                      | [notification](review-07-notification.md#n3)           |

## Thứ tự nên sửa

1. **Cho app chạy đúng thông báo:** `N1`
2. **Khắc phục gửi sai nội dung / lỗi null thông báo:** `N2`
3. **Chuẩn hóa response thông báo:** `N3`
4. **Chặn sửa giao dịch gốc của nợ (khi hoàn thiện module debt):** `T6`
