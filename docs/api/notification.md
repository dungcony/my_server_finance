# ĐẶC TẢ RESTFUL API ENDPOINTS — THÔNG BÁO (NOTIFICATION)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Thông Báo Người Dùng (`/notifications`), lọc theo trạng thái đọc, đánh dấu đã đọc, và kịch bản kiểm thử API.  
> Code chính → [`NotificationController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/notification/controller/NotificationController.java) · [`NotificationService.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/notification/service/NotificationService.java)

---

## 1. Quy ước chung

- **Base URL:** `/notifications`
- **Định dạng dữ liệu:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Xác thực phiên:** `Authorization: Bearer <accessToken>`.

---

## 2. Danh mục Endpoint Tổng hợp

| Phương thức | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| **`GET`** | `/notifications` | Đã đăng nhập | Lấy danh sách thông báo của người dùng (phân trang, lọc theo `is_read`) |
| **`PATCH`** | `/notifications/{id}/read` | Đã đăng nhập | Đánh dấu một thông báo đã đọc |

---

## 3. Đặc tả Chi tiết Endpoints

### 3.1 Danh sách thông báo (`GET /notifications`)

- **Method / Path:** `GET /notifications`
- **Query Parameters:**
  - `is_read` (Boolean, optional): `true` (đã đọc), `false` (chưa đọc), bỏ trống (tất cả).
  - `page`, `size`, `sort`.
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<NotificationListResponse>`):**
```json
{
  "success": true,
  "code": 200,
  "message": "Thành công",
  "data": {
    "notifications": [
      {
        "id": "c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f",
        "title": "Cảnh báo vượt ngân sách",
        "content": "Ngân sách ăn uống tháng 10 đã đạt 85% hạn mức.",
        "type": "BUDGET_ALERT",
        "is_read": false,
        "created_at": "2026-10-19T09:00:00Z"
      }
    ],
    "unread_count": 1,
    "page": {
      "page_number": 0,
      "page_size": 20,
      "total_elements": 1,
      "total_pages": 1
    }
  }
}
```

---

### 3.2 Đánh dấu đã đọc (`PATCH /notifications/{id}/read`)

- **Method / Path:** `PATCH /notifications/{id}/read`
- **Path Variables:** `id` (UUID, required) — ID thông báo.
- **Response Status:** `200 OK`
- **Response Body:** `ApiResponse<Void>`
- **Mã lỗi:** `404 Not Found` nếu thông báo không tồn tại hoặc không thuộc người dùng.
