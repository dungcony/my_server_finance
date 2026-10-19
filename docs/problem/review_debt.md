# Vấn đề của DEBT

## Mục lục
- [1. Bản chất gắn ví của Debt](#1-bản-chất-gắn-ví-của-debt)
- [2. Sửa hoặc xoá giao dịch liên kết từ Module Transaction](#2-sửa-hoặc-xoá-giao-dịch-liên-kết-từ-module-transaction)
- [3. Xoá sổ nợ làm lệch số dư ví ngoài đời](#3-xoá-sổ-nợ-làm-lệch-số-dư-ví-ngoài-đời)
- [4. Trạng thái written_off gây bất cập nghiệp vụ](#4-trạng-thái-written_off-gây-bất-cập-nghiệp-vụ)
- [5. Xoá cứng (Hard Delete) thay vì Xoá mềm (Soft Delete)](#5-xoá-cứng-hard-delete-thay-vì-xoá-mềm-soft-delete)
- [6. Xoá ví làm mồ côi khoản nợ](#6-xoá-ví-làm-mồ-côi-khoản-nợ)
- [7. Tổng hợp quyền User/Admin & Kế hoạch sửa đổi](#7-tổng-hợp-quyền-useradmin--kế-hoạch-sửa-đổi)

---

## 1. Bản chất gắn ví của Debt

### Vấn đề
- Khác với Goal (tiền luân chuyển nội bộ/bỏ heo ảo có thể bỏ gắn ví để đơn giản hóa), Debt là dòng tiền thật với bên ngoài:
  - Cho vay (`lending`) $\rightarrow$ Rút tiền khỏi ví $\rightarrow$ Sinh giao dịch `EXPENSE` (danh mục `cho_vay`).
  - Đi vay (`borrowing`) $\rightarrow$ Nhận tiền vào ví $\rightarrow$ Sinh giao dịch `INCOME` (danh mục `di_vay`).
  - Thu nợ $\rightarrow$ Tiền về ví $\rightarrow$ Sinh giao dịch `INCOME` (danh mục `thu_no`).
  - Trả nợ $\rightarrow$ Tiền rời ví $\rightarrow$ Sinh giao dịch `EXPENSE` (danh mục `tra_no`).
- Nếu Debt không gắn ví thì số dư tiền mặt/ngân hàng trên App sẽ lệch ngay lập tức so với thực tế ngoài đời.

### Phương pháp
- Giữ nguyên cơ chế Debt bắt buộc gắn Wallet và tự động sinh Transaction tương ứng.
- Khi tạo giao dịch cho khoản nợ, chỉ lấy các trường cần thiết phục vụ dòng tiền: `amount`, `walletId`, `type`, `occurredAt`.

### Code dự kiến
- Giữ nguyên luồng sinh giao dịch hiện tại trong `DebtServiceImpl.create` và `DebtServiceImpl.addPayment`.

---

## 2. Sửa hoặc xoá giao dịch liên kết từ Module Transaction

### Vấn đề
- Khi người dùng vào màn hình Giao dịch:
  - Xóa giao dịch: Hiện đã có trigger DB (`trg_transactions_block_debt_delete`) chặn xóa mềm.
  - Sửa giao dịch: Chưa chặn sửa `amount`, `walletId`, `type`. Người dùng sửa số tiền hoặc đổi ví thì tiền ví bị nhảy lại theo số mới, nhưng Sổ nợ vẫn giữ nguyên số cũ $\rightarrow$ Gây lệch tiền vĩnh viễn giữa Ví và Sổ nợ.

### Kịch bản lỗi
- Người dùng tạo khoản nợ cho Nam vay 2.000.000đ (Ví A bị trừ 2.000.000đ).
- Sau đó vào màn hình Lịch sử giao dịch sửa số tiền giao dịch đó từ 2.000.000đ thành 500.000đ.
- Kết quả: Ví A được hoàn lại 1.500.000đ (chỉ còn trừ 500k), nhưng trong Sổ nợ khoản nợ của Nam vẫn là 2.000.000đ $\rightarrow$ Lệch 1.500.000đ giữa sổ sách và ví.

### Phương pháp
- Chặn sửa các trường nhạy cảm (`amount`, `walletId`, `type`) đối với các giao dịch đang liên kết với khoản nợ (giao dịch gốc hoặc các lần trả nợ).
- Chỉ cho phép sửa thông tin mô tả: `name`, `note`, `occurredAt`.

### Code dự kiến
- Trong `TxnBehavierImpl.update`: Thêm kiểm tra nếu `transactionId` tồn tại trong bảng `debts` (`origin_transaction_id`) hoặc trong `debt_payments` (`transaction_id`):
  - Nếu `req.amount() != null` hoặc `req.walletId() != null` hoặc `req.type() != null` $\rightarrow$ Ném ngoại lệ chặn lại:
    `"Không thể sửa số tiền/ví/loại của giao dịch liên kết với sổ nợ. Vui lòng quản lý từ mục Sổ nợ."`

---

## 3. Xoá sổ nợ làm lệch số dư ví ngoài đời

### Vấn đề
- Khi xóa sổ nợ, hệ thống tự động xóa mềm cả giao dịch gốc lẫn tất cả các giao dịch trả nợ để hoàn tác số dư ví.
- Nếu khoản nợ đã phát sinh thanh toán thật ngoài đời từ trước, việc xóa sổ nợ sẽ âm thầm đảo ngược lại các giao dịch quá khứ, khiến số dư ví trên App bị lệch hoàn toàn so với tài khoản ngân hàng thực tế.

### Kịch bản lỗi

| Thời điểm / Hành động | Tài khoản ngân hàng thật | Số dư Ví trên App | Trạng thái |
| :--- | :--- | :--- | :--- |
| **1. Ban đầu** | 10.000.000đ | 10.000.000đ | Khớp |
| **2. Chuyển khoản cho bạn vay 1.000.000đ** | Còn 9.000.000đ | Còn 9.000.000đ *(tạo nợ + txn Chi 1tr)* | Khớp |
| **3. Bạn bè chuyển khoản trả 300.000đ** | Lên **9.300.000đ** | Lên **9.300.000đ** *(trả nợ + txn Thu 300k)* | Khớp |
| **4. Người dùng bấm XÓA SỔ NỢ trên App** | **Vẫn là 9.300.000đ** *(ngoài đời chưa trả 700k)* | Nhảy lên **10.000.000đ** *(App tự hoàn tác +700k)* | ❌ **LỆCH 700.000đ** |

### Phương pháp
- Xử lý theo tiến độ thanh toán của khoản nợ:
  1. **Chưa trả lần nào (`paidAmount == 0`):** Cho phép XÓA và hoàn tác tiền ví bình thường (hiểu là tạo nhầm nên hủy).
  2. **Đang trả dở (`paidAmount > 0`):** **CHẶN XÓA.** Muốn xóa phải vào xóa từng đợt trả nợ trước (`cancelPayment`) để người dùng ý thức rõ từng khoản tiền bị hoàn tác.
  3. **Đã trả xong (`settled`):** Không cho xóa, giữ nguyên lịch sử giao dịch.

### Code dự kiến
- Trong `DebtServiceImpl.delete`:
  - Kiểm tra nếu `debt.getPaidAmount() > 0`:
    `throw new BusinessException(ErrorCode.DEBT_CANNOT_DELETE_WITH_PAYMENTS, "Khoản nợ đã có lịch sử thanh toán, không thể xóa trực tiếp. Vui lòng xóa từng lần trả nợ trước nếu muốn hủy.");`
  - Nếu `debt.getPaidAmount() == 0`: Cho phép xóa nợ và hoàn tác giao dịch gốc.

---

## 4. Trạng thái written_off gây bất cập nghiệp vụ

### Vấn đề
- Trạng thái `written_off` (xóa nợ khó đòi) làm phức tạp luồng nghiệp vụ.
- Cho phép bấm cả với khoản Đi vay (`borrowing`), gây vô lý (đi vay mà tự xóa nợ là quỵt nợ).
- Khóa 1 chiều, nếu đối tác bất ngờ trả lại tiền thì hệ thống chặn cứng không cho ghi nhận tiếp.

### Phương pháp
- Bỏ hoàn toàn trạng thái `written_off`.
- Sổ nợ chỉ duy trì **2 trạng thái**:
  - `outstanding`: Đang nợ (chưa trả hoặc mới trả một phần).
  - `settled`: Đã thanh toán xong toàn bộ.

### Code dự kiến
- Bỏ endpoint `POST /debts/{id}/write-off` và class `WriteOffRequest`.
- Bỏ constant `STATUS_WRITTEN_OFF = "written_off"` và hàm `writeOff` trong `DebtServiceImpl`.
- Sửa DTOs: `DebtDetailResponse`, `DebtListItemResponse`.
- Migration DB:
  - Cập nhật constraint: `ck_debt_status CHECK (status IN ('outstanding', 'settled'))`.
  - Sửa trigger `fn_debt_payments_sync`: Bỏ nhánh xử lý `written_off`.

---

## 5. Xoá cứng (Hard Delete) thay vì Xoá mềm (Soft Delete)

### Vấn đề
- Cả 2 bảng `debts` và `debt_payments` đều có cột `is_deleted` và các câu query JPA đều lọc `WHERE NOT is_deleted`.
- Tuy nhiên trong `DebtServiceImpl.delete` và `cancelPayment` lại gọi `repository.delete(...)` (xóa cứng vật lý mất khỏi DB), làm mất dấu vết kiểm toán và không đúng chuẩn soft delete chung của toàn dự án.

### Phương pháp
- Chuyển toàn bộ thao tác xóa của `Debt` và `DebtPayment` sang cập nhật cờ `is_deleted = true`.

### Code dự kiến
- Trong `DebtServiceImpl.delete`:
  ```java
  debt.setIsDeleted(true);
  debtRepository.save(debt);
  ```
- Trong `DebtServiceImpl.cancelPayment`:
  ```java
  payment.setIsDeleted(true);
  debtPaymentRepository.save(payment);
  ```
- Cập nhật trigger `fn_debt_payments_sync` để lắng nghe thêm sự kiện `UPDATE OF is_deleted ON debt_payments` giúp tính toán lại `paid_amount` chính xác khi xóa mềm.

---

## 6. Xoá ví làm mồ côi khoản nợ

### Vấn đề
- Khi xóa mềm ví, hệ thống không kiểm tra ví có đang liên kết với khoản nợ `outstanding` hay không.
- Khiến khoản nợ bị mồ côi ví, sau này khi người dùng ghi nhận trả nợ (hệ thống mặc định lấy ví cũ của nợ) sẽ bị lỗi không tìm thấy ví.

### Kịch bản lỗi
- Người dùng tạo nợ cho vay 5.000.000đ gắn với Ví Tiền mặt (Ví A).
- Người dùng vào mục Quản lý ví bấm xóa Ví A $\rightarrow$ Ví A bị xóa mềm.
- Sau đó vào mục Sổ nợ bấm "Trả nợ" $\rightarrow$ Code mặc định lấy `debt.walletId` (Ví A) để nạp tiền $\rightarrow$ Quăng lỗi `ErrorCode.NOT_FOUND ("Không tìm thấy ví hợp lệ")`.

### Phương pháp
- Chặn xóa ví nếu ví đang liên kết với khoản nợ chưa tất toán (`status = 'outstanding'`), tương tự như cơ chế của ví Tiết kiệm.

### Code dự kiến
- Trong `DebtRepository`: Thêm method kiểm tra:
  `boolean existsByWalletIdAndStatusAndIsDeletedFalse(UUID walletId, String status);`
- Trong `WalletBehavierImpl.softDelete`:
  - Thêm điều kiện kiểm tra:
    ```java
    boolean hasActiveDebt = debtRepository.existsByWalletIdAndStatusAndIsDeletedFalse(walletId, "outstanding");
    if (hasActiveDebt) {
        throw new BusinessException(ErrorCode.WALLET_LINKED_TO_DEBT, "Ví đang liên kết với khoản nợ chưa tất toán. Vui lòng tất toán nợ trước khi xóa ví.");
    }
    ```

---

## 7. Tổng hợp quyền User/Admin & Kế hoạch sửa đổi

### User có thể làm:
- Tạo khoản nợ (Cho vay / Đi vay) gắn với ví nguồn.
- Trả nợ / Thu nợ (toàn phần hoặc từng phần) bằng ví tùy chọn.
- Hủy đợt trả nợ đã tạo sai (`cancelPayment`).
- Xóa khoản nợ khi chưa trả lần nào (`paidAmount == 0`).
- Sửa hạn trả, tên đối tác, ghi chú khoản nợ.
- Xem danh sách, chi tiết, thống kê nợ và lịch sử thanh toán.

### Admin có thể làm:
- Xem danh sách nợ của từng user hoặc toàn hệ thống.
- Hỗ trợ tra cứu kiểm toán lịch sử nợ (nhờ cơ chế xóa mềm).

### Kế hoạch sửa đổi code:
1. **Database & Migrations:** Sửa constraint `ck_debt_status`, cập nhật trigger `fn_debt_payments_sync` và `fn_transactions_block_debt_delete`.
2. **Module Transaction:** `TxnBehavierImpl.update` chặn sửa `amount`, `walletId`, `type` của giao dịch liên kết nợ.
3. **Module Wallet:** `WalletBehavierImpl.softDelete` chặn xóa ví đang có nợ `outstanding`.
4. **Module Debt:**
   - Bỏ API `writeOff` và DTO `WriteOffRequest`.
   - `DebtServiceImpl.delete`: Chặn xóa khi `paidAmount > 0`, chuyển sang xóa mềm `is_deleted = true`.
   - `DebtServiceImpl.cancelPayment`: Chuyển sang xóa mềm `is_deleted = true`.
