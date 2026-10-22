# ĐẶC TẢ RESTFUL API ENDPOINTS — BIỂU TƯỢNG HỆ THỐNG (ICON)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Biểu Tượng Hệ Thống (`/icons`), quản trị kho SVG path cho danh mục và ví, phân quyền RBAC, và kịch bản kiểm thử API.  
> Code chính → [`IconController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/icon/controller/IconController.java) · [`IconBehavierService.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/icon/service/IconBehavierService.java)

---

## 1. Quy ước chung

- **Base URL:** `/icons`
- **Định dạng dữ liệu:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Phân quyền RBAC:**
  - Đọc (`GET /icons`): Mọi người dùng đã đăng nhập để chọn icon cho ví và danh mục.
  - Tạo/Sửa (`POST`, `PATCH`): Quyền `icons:update` (`PermissionName.ICONS_UPDATE`).
  - Xóa (`DELETE`): Quyền `icons:delete` (`PermissionName.ICONS_DELETE`).

---

## 2. Danh mục Endpoint Tổng hợp

| Phương thức | Endpoint | Quyền hạn | Idempotent | Mô tả |
| :--- | :--- | :--- | :---: | :--- |
| **`GET`** | `/icons` | Đã đăng nhập | Không | Lấy danh sách biểu tượng hệ thống (phân trang dạng Slice) |
| **`POST`** | `/icons` | `icons:update` | **Có** | Admin thêm biểu tượng SVG mới vào kho hệ thống |
| **`PATCH`** | `/icons/{code}` | `icons:update` | Không | Admin cập nhật tên hiển thị hoặc mã SVG path |
| **`DELETE`** | `/icons/{code}` | `icons:delete` | Không | Admin xóa mềm biểu tượng (`is_deleted = true`) |

---

## 3. Đặc tả Chi tiết Endpoints

### 3.1 Danh sách biểu tượng (`GET /icons`)
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<Slice<IconRes>>`):** Trả về danh sách `code`, `display_name`, `path_data` (chuỗi SVG path).

### 3.2 Tạo mới biểu tượng (`POST /icons`)
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`IconCreateReq`):**
```json
{
  "code": "may_bay",
  "display_name": "Máy bay du lịch",
  "path_data": "M2 14l9-2.5V5a1.5 1.5 0 013 0v6.5L23 14v2l-9-1.5V19l2.5 1.5v1.5L12 21l-4.5 1v-1.5L10 19v-4.5L1 16z"
}
```
- **Response Status:** `201 Created`
- **Mã lỗi:** `ICON_CODE_EXISTS` (409 Conflict) nếu mã code đã tồn tại.

---

## 4. Bảng Ma trận Mã lỗi (Error Code Matrix)

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `ICON_CODE_EXISTS` | `409 Conflict` | Mã biểu tượng đã tồn tại trong kho |
| `ICON_NOT_FOUND` | `404 Not Found` | Biểu tượng không tồn tại hoặc đã bị ngừng sử dụng |
| `FORBIDDEN` | `403 Forbidden` | Thiếu quyền `icons:update` hoặc `icons:delete` |
| `VALIDATION_ERROR` | `400 Bad Request` | Thiếu trường bắt buộc hoặc mã sai quy cách |
