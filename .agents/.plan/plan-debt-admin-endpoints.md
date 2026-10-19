# Kế hoạch bổ sung Service và API Admin cho Module Debt

## 1. Bối cảnh & Mục tiêu

- Module `debt` hiện tại đã hoàn thiện nghiệp vụ dành cho người dùng thông thường (`DebtService`, `DebtServiceImpl`, `DebtController`).
- Người dùng yêu cầu **bổ sung các endpoint dành riêng cho Admin**, đồng thời **tách hẳn một Service riêng cho Admin (`DebtAdminService`, `DebtAdminServiceImpl`)** thay vì nhồi nhét chung vào Service của user để code gọn gàng, độc lập và dễ debug/bảo trì.
- Tuân thủ quy chuẩn phân quyền hệ thống dựa trên `PermissionName` (`Resource.DEBT`, các hành động `READ`, `UPDATE`, `DELETE`).

---

## 2. Quyết định kỹ thuật

1. **Phân tách tầng Service & Controller:**
   - **Tầng Service:** Tạo mới interface `DebtAdminService` và lớp cài đặt `DebtAdminServiceImpl` độc lập hoàn toàn với `DebtServiceImpl`.
   - **Tầng Controller:** Bổ sung các endpoint admin (`/debts/admin/...`) vào `DebtController` (hoặc tạo `DebtAdminController`), bảo vệ bằng `@PreAuthorize("hasAuthority(...)")`.
2. **Quyền hạn (RBAC):**
   - Sử dụng các enum đã khai báo trong `PermissionName`:
     - `PermissionName.DEBT_READ` (`debts:read`): Dành cho các tác vụ xem danh sách, chi tiết, tổng quan.
     - `PermissionName.DEBT_UPDATE` (`debts:update`): Dành cho tạo nợ hộ user, trả nợ hộ user, cập nhật nợ, duyệt xóa nợ xấu (write-off).
     - `PermissionName.DEBT_DELETE` (`debts:delete`): Dành cho hủy lần trả nợ, xóa cưỡng chế khoản nợ và hoàn tác dòng tiền.
3. **Repository:**
   - Bổ sung các query hỗ trợ Admin (không bắt buộc `user_id`, cho phép lọc theo `user_id` tùy chọn, `type`, `status`):
     - `findAllForAdmin(UUID userId, String type, String status)`
     - `findSummaryForAdmin(UUID userId, String status)`
     - `findByIdAndIsDeletedFalse(UUID id)`

---

## 3. Các file sẽ tạo mới & thay đổi

### 1. Phân quyền (Enums & Seed)
- `src/main/java/com/datn/financeapp/module/user/enums/PermissionName.java`: Đã có `DEBT_READ`, `DEBT_UPDATE`, `DEBT_DELETE` thuộc `Resource.DEBT`.

### 2. Tầng Repository
- `src/main/java/com/datn/financeapp/module/debt/repository/DebtRepository.java`:
  - Thêm `findByIdAndIsDeletedFalse(UUID id)` (lấy chi tiết nợ của bất kỳ user nào).
  - Thêm `findAllForAdmin(@Param("userId") UUID userId, @Param("type") String type, @Param("status") String status)`.

### 3. Tầng Service Admin (Tạo mới)
- `src/main/java/com/datn/financeapp/module/debt/service/DebtAdminService.java` *(file mới)*: Interface định nghĩa các nghiệp vụ admin:
  - `List<DebtListItemResponse> list(UUID userId, String type, String status, Boolean isOverdue)`: Lấy danh sách nợ toàn hệ thống hoặc theo user.
  - `DebtDetailResponse detail(UUID debtId)`: Lấy chi tiết khoản nợ của bất kỳ user nào.
  - `DebtSummaryResponse summary(UUID userId)`: Lấy báo cáo tổng quan nợ toàn hệ thống hoặc của một user.
  - `CreateDebtResponse createForUser(UUID targetUserId, CreateDebtRequest req)`: Tạo khoản nợ hộ user.
  - `CreatePaymentResponse addPaymentForUser(UUID debtId, CreatePaymentRequest req)`: Ghi nhận trả nợ/thu nợ hộ user.
  - `DebtListItemResponse update(UUID debtId, UpdateDebtRequest req)`: Cập nhật thông tin nợ.
  - `DebtListItemResponse writeOff(UUID debtId, WriteOffRequest req)`: Duyệt xóa nợ xấu.
  - `void cancelPayment(UUID debtId, UUID paymentId)`: Hủy một đợt trả nợ và hoàn tác ví.
  - `void delete(UUID debtId)`: Xóa cưỡng chế khoản nợ và hoàn tác các giao dịch liên quan.
- `src/main/java/com/datn/financeapp/module/debt/service/impl/DebtAdminServiceImpl.java` *(file mới)*: Cài đặt chi tiết các hàm trên, tích hợp với `TxnBehavierService`, `WalletService`, `DebtRepository`, `DebtPaymentRepository`.

### 4. Tầng Controller
- Bổ sung vào `src/main/java/com/datn/financeapp/module/debt/controller/DebtController.java`:
  - `GET /debts/admin`: Danh sách nợ toàn hệ thống hoặc lọc theo `user_id`.
  - `GET /debts/admin/summary`: Tổng quan công nợ hệ thống hoặc theo `user_id`.
  - `GET /debts/admin/{id}`: Chi tiết khoản nợ.
  - `POST /debts/admin`: Tạo nợ hộ user (truyền `@RequestParam("user_id") UUID userId`).
  - `PATCH /debts/admin/{id}`: Cập nhật nợ.
  - `POST /debts/admin/{id}/payments`: Ghi nhận thanh toán nợ hộ user.
  - `POST /debts/admin/{id}/write-off`: Xóa nợ xấu.
  - `DELETE /debts/admin/{id}/payments/{paymentId}`: Hủy đợt thanh toán nợ.
  - `DELETE /debts/admin/{id}`: Cưỡng chế xóa khoản nợ.

---

## 4. Bảng công việc chi tiết

| # | Công việc | File thực hiện | Trạng thái |
|---|---|---|---|
| 1 | Bổ sung query Admin trong Repository | `DebtRepository.java` | Hoàn thành |
| 2 | Tạo interface `DebtAdminService` | `module/debt/service/DebtAdminService.java` | Hoàn thành |
| 3 | Tạo implementation `DebtAdminServiceImpl` | `module/debt/service/impl/DebtAdminServiceImpl.java` | Hoàn thành |
| 4 | Bổ sung các endpoint Admin vào Controller | `module/debt/controller/DebtController.java` | Hoàn thành |
| 5 | Biên dịch và kiểm tra tính tương thích | `mvn compile -DskipTests` | Hoàn thành |
