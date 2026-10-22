# Chuẩn hóa luồng nghiệp vụ và khắc phục lỗi dữ liệu trong bộ test Bruno

## Bối cảnh

1. Khi chạy kiểm thử toàn bộ API qua Bruno CLI (`npx @usebruno/cli run . -r --env Local`), hàng loạt request chi tiết, sửa và xoá trong các module (`Transactions`, `Budgets`, `Debts`, `Goals`) bị lỗi `400 Bad Request` do thiếu ID động (`{{transactionId}}`, `{{budgetId}}`...). Trong tài liệu mô tả (`docs`) của các request tạo mới đều ghi "Tự động lưu ID", nhưng thực tế file chưa có script `postResponse`.
2. Request xoá trong `04-Categories` và `14-Icons` đang xoá trực tiếp dữ liệu danh mục gốc (`an_uong`) và biểu tượng (`coffee`), khiến các module chạy phía sau (`Wallets`, `Transactions`, `Budgets`, `Recurring`) không còn danh mục/icon để tạo mới và vỡ dây chuyền.
3. Một số request gửi sai Enum (`role_permission:read` thay vì `ROLE_PERMISSION_READ`) hoặc gửi query param rỗng (`10-Goals/Admin/01-List-All-Goals.yml`) gây lỗi.

## Quyết định đã chốt

- **Bảo vệ dữ liệu master**:
  - `14-Icons/Admin`: Tạo biểu tượng test tạm thời `temp_icon`, cập nhật và xoá `temp_icon`; KHÔNG xoá biểu tượng dùng chung `coffee`.
  - `04-Categories/Admin`: Tạo danh mục test tạm thời `test_cat`, cập nhật và xoá `test_cat`; KHÔNG xoá danh mục gốc `an_uong`.
- **Tự động lưu biến qua `runtime: postResponse`**:
  - `01-Auth/Admin/01-Login-Admin.yml`: Lưu `admin_access` từ response token.
  - `05-Transactions/User/04-Create-Transaction.yml`: Lưu `transactionId`.
  - `06-Budgets/User/06-Create-Budget.yml`: Lưu `budgetId`.
  - `08-Debts/User/04-Create-Debt.yml`: Lưu `debtId`.
  - `10-Goals/User/03-Create-Goal.yml`: Lưu `goalId`.
- **Sửa cú pháp Enum & Dọn query param rỗng**:
  - Sửa `role_permission:read` thành `ROLE_PERMISSION_READ` trong `02-Users/Admin/03-Assign-Permission-To-Role.yml`.
  - Xoá các query param rỗng (`userId`, `status`, `name`, `targetDateFrom`, `targetDateTo`) trong `10-Goals/Admin/01-List-All-Goals.yml`.
- **Request SSE Stream Logs**:
  - Đổi tên thành `10-Stream-Logs.yml.skip` để bỏ qua khi chạy tự động hàng loạt qua CLI (tránh lỗi timeout).

## 1. Các file đã thay đổi

**Tài liệu / Cấu hình Bruno**:

| STT | File | Nội dung thay đổi |
|---|---|---|
| 1 | `bruno/01-Auth/Admin/01-Login-Admin.yml` | Thêm `runtime: postResponse` lưu `admin_access` |
| 2 | `bruno/02-Users/Admin/03-Assign-Permission-To-Role.yml` | Sửa `role_permission:read` thành `ROLE_PERMISSION_READ` |
| 3 | `bruno/02-Users/Admin/10-Stream-Logs.yml` | Đổi tên thành `10-Stream-Logs.yml.skip` |
| 4 | `bruno/04-Categories/Admin/01-Create-Category.yml` | Tạo code `test_cat`, lưu biến `tempCategoryCode` |
| 5 | `bruno/04-Categories/Admin/02-Update-Category.yml` | Cập nhật theo `{{tempCategoryCode}}` |
| 6 | `bruno/04-Categories/Admin/03-Delete-Category.yml` | Xoá theo `{{tempCategoryCode}}` (thay vì `an_uong`) |
| 7 | `bruno/05-Transactions/User/04-Create-Transaction.yml` | Thêm `runtime: postResponse` lưu `transactionId` |
| 8 | `bruno/06-Budgets/User/06-Create-Budget.yml` | Thêm `runtime: postResponse` lưu `budgetId` |
| 9 | `bruno/08-Debts/User/04-Create-Debt.yml` | Thêm `runtime: postResponse` lưu `debtId` |
| 10 | `bruno/10-Goals/Admin/01-List-All-Goals.yml` | Bỏ các query param rỗng |
| 11 | `bruno/10-Goals/User/03-Create-Goal.yml` | Thêm `runtime: postResponse` lưu `goalId` |
| 12 | `bruno/14-Icons/Admin/01-Create-Icon.yml` | Tạo code `temp_icon`, lưu biến `tempIconCode` |
| 13 | `bruno/14-Icons/Admin/02-Update-Icon.yml` | Cập nhật theo `{{tempIconCode}}` |
| 14 | `bruno/14-Icons/Admin/03-Delete-Icon.yml` | Xoá theo `{{tempIconCode}}` (thay vì `coffee`) |
| 15 | `bruno/environments/Local.yml` | Khai báo bổ sung `tempCategoryCode`, `tempIconCode` |

*Các file không thay đổi*: Mã nguồn Java.

## 2. Lý do thay đổi

- **Nguyên nhân chuỗi lỗi 400**: Các request chi tiết, sửa, xoá yêu cầu ID (`UUID`), nhưng request tạo mới trước đó không có script trích xuất ID từ response body để gán vào môi trường Bruno.
- **Nguyên nhân chuỗi lỗi 404 (Not Found)**: Request `03-Delete-Category` xoá mất `an_uong` và `03-Delete-Icon` xoá mất `coffee`, dẫn đến toàn bộ request tạo ví, tạo giao dịch, tạo ngân sách chạy sau đó đều thất bại vì danh mục và icon không tồn tại.
- **Lỗi 400 Enum**: Backend định nghĩa enum `PermissionName` dạng chữ hoa gạch dưới (`ROLE_PERMISSION_READ`). Request Bruno gửi chuỗi `role_permission:read` khiến Jackson deserializer ném lỗi.

## 3. Hướng giải quyết / code xem trước

### A. Tự động lưu biến sau khi tạo mới (ví dụ `05-Transactions/User/04-Create-Transaction.yml`):
```yaml
runtime:
  postResponse: |-
    if ((res.status === 201 || res.status === 200) && res.body && res.body.data && res.body.data.id) {
      bru.setEnvVar("transactionId", res.body.data.id);
    }
```

### B. Bảo vệ master data tại Categories và Icons:
- Tại `04-Categories/Admin/01-Create-Category.yml`:
  ```yaml
  body:
    type: json
    data: |-
      {
        "name": "Danh mục test",
        "code": "test_cat",
        "description": "Dùng cho kiểm thử tự động",
        "icon_code": "coffee",
        "color": "#3D6B7D",
        "parent_code": "an_uong"
      }
  runtime:
    postResponse: |-
      if (res.status === 201 && res.body && res.body.data && res.body.data.code) {
        bru.setEnvVar("tempCategoryCode", res.body.data.code);
      }
  ```
- Tại `04-Categories/Admin/03-Delete-Category.yml`:
  ```yaml
  url: "{{baseUrl}}/categories/{{tempCategoryCode}}"
  ```

### C. Sửa Enum trong `02-Users/Admin/03-Assign-Permission-To-Role.yml`:
```yaml
body:
  type: json
  data: |-
    {
      "role_name": "ROLE_ADMIN",
      "permission_name": "ROLE_PERMISSION_READ"
    }
```

## 4. Danh sách công việc chi tiết

1. Cập nhật `01-Login-Admin.yml` thêm script `postResponse` lưu token Admin.
2. Sửa Enum trong `02-Users/Admin/03-Assign-Permission-To-Role.yml`.
3. Chuẩn hóa bộ `04-Categories/Admin` (dùng `tempCategoryCode` thay vì xóa `an_uong`).
4. Bổ sung `postResponse` lưu ID cho `05-Transactions`, `06-Budgets`, `08-Debts`, `10-Goals`.
5. Dọn query params rỗng trong `10-Goals/Admin/01-List-All-Goals.yml`.
6. Chuẩn hóa bộ `14-Icons/Admin` (dùng `tempIconCode` thay vì xóa `coffee`).
7. Đổi tên `10-Stream-Logs.yml` sang `.skip` để tránh timeout khi chạy tự động.

## Ngoài phạm vi

- Mã nguồn Java của server (người dùng đang tự chỉnh sửa).
- Module `13-Groups` và `15-Payments`.

## Kiểm tra

- Luôn hỏi trước khi chạy, không tự ý chạy test nếu không có yêu cầu.
- Lệnh PowerShell kiểm tra Bruno CLI sau khi sửa:
  ```powershell
  cd d:\projects\java\server\bruno
  npx @usebruno/cli run 04-Categories 05-Transactions 06-Budgets -r --env Local
  ```

## Câu hỏi còn mở

- Không còn câu hỏi mở.

## Bảng công việc

| STT | Nhiệm vụ | Trạng thái |
|---|---|---|
| 1 | Thêm `postResponse` lưu token trong `01-Login-Admin.yml` | Hoàn thành |
| 2 | Sửa Enum trong `03-Assign-Permission-To-Role.yml` | Hoàn thành |
| 3 | Tách danh mục test tạm thời trong `04-Categories/Admin` | Hoàn thành |
| 4 | Thêm `postResponse` lưu ID cho Transactions, Budgets, Debts, Goals | Hoàn thành |
| 5 | Dọn query param rỗng trong `10-Goals/Admin/01-List-All-Goals.yml` | Hoàn thành |
| 6 | Tách icon test tạm thời trong `14-Icons/Admin` | Hoàn thành |
| 7 | Đổi tên `10-Stream-Logs.yml` sang `.skip` | Hoàn thành |
