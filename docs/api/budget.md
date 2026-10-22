# ĐẶC TẢ RESTFUL API ENDPOINTS — NGÂN SÁCH CHI TIÊU (BUDGET)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Ngân Sách (`/budgets`), cảnh báo ngân sách (`alerts`), tổng quan chi tiêu (`summary`), luồng Admin cưỡng chế (`/budgets/admin`), và kịch bản kiểm thử API.  
> Code chính → [`BudgetController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/controller/BudgetController.java) · [`BudgetAdminController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/budget/controller/BudgetAdminController.java)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Người dùng](#3-dac-ta-chi-tiet-endpoints-nguoi-dung)
  - [3.1 Danh sách ngân sách (`GET /budgets`)](#31-danh-sach-ngan-sach)
  - [3.2 Báo cáo tổng quan ngân sách (`GET /budgets/summary`)](#32-tong-quan-ngan-sach)
  - [3.3 Danh sách cảnh báo ngân sách (`GET /budgets/alerts`)](#33-canh-bao-ngan-sach)
  - [3.4 Chi tiết một ngân sách (`GET /budgets/{id}`)](#34-chi-tiet-ngan-sach)
  - [3.5 Tạo mới ngân sách (`POST /budgets`)](#35-tao-ngan-sach)
  - [3.6 Cập nhật ngân sách (`PATCH /budgets/{id}`)](#36-cap-nhat-ngan-sach)
  - [3.7 Xóa ngân sách (`DELETE /budgets/{id}`)](#37-xoa-ngan-sach)
- [4. Đặc tả Chi tiết Endpoints Quản trị viên (`/budgets/admin`)](#4-dac-ta-chi-tiet-endpoints-admin)
- [5. Bảng Ma trận Mã lỗi (Error Code Matrix)](#5-bang-ma-tran-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/budgets`
- **Định dạng dữ liệu:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Xác thực:** Header `Authorization: Bearer <accessToken>`.
- **Kỳ ngân sách (`PeriodType`):** `WEEK` (Tuần), `MONTH` (Tháng), `QUARTER` (Quý), `YEAR` (Năm).
- **Quy tắc bất biến:** Một danh mục trong cùng một kỳ chỉ được có tối đa 1 ngân sách đang hoạt động (`BUDGET_ALREADY_EXISTS`). Không cho phép đổi danh mục hoặc kỳ ngân sách qua `PATCH` (`BUDGET_CATEGORY_NOT_EDITABLE`, muốn đổi phải xóa và tạo mới).

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Quyền hạn | Idempotent | Mô tả |
| :--- | :--- | :--- | :---: | :--- |
| **`GET`** | `/budgets` | Đã đăng nhập | Không | Danh sách ngân sách của người dùng theo trạng thái và kỳ |
| **`GET`** | `/budgets/summary` | Đã đăng nhập | Không | Báo cáo tổng quan: tổng hạn mức, đã tiêu, còn lại, tiến độ % |
| **`GET`** | `/budgets/alerts` | Đã đăng nhập | Không | Danh sách cảnh báo ngân sách sắp chạm ngưỡng (80%) hoặc vượt hạn mức |
| **`GET`** | `/budgets/{id}` | Đã đăng nhập | Không | Chi tiết ngân sách theo ID |
| **`POST`** | `/budgets` | Đã đăng nhập | **Có** | Tạo mới ngân sách chi tiêu |
| **`PATCH`** | `/budgets/{id}` | Đã đăng nhập | Không | Cập nhật hạn mức, ngưỡng cảnh báo, ghi chú |
| **`DELETE`** | `/budgets/{id}` | Đã đăng nhập | Không | Xóa (vô hiệu hóa) ngân sách |
| **`GET`** | `/budgets/admin` | `budgets:read` | Không | Admin tra cứu danh sách ngân sách toàn hệ thống |
| **`GET`** | `/budgets/admin/{id}` | `budgets:read` | Không | Admin xem chi tiết ngân sách bất kỳ |
| **`DELETE`** | `/budgets/admin/{id}` | `budgets:delete` | Không | Admin cưỡng chế xóa hoặc vô hiệu hóa ngân sách |

---

## 3. Đặc tả Chi tiết Endpoints Người dùng <a id="3-dac-ta-chi-tiet-endpoints-nguoi-dung"></a>

### 3.1 Báo cáo tổng quan ngân sách (`GET /budgets/summary`) <a id="32-tong-quan-ngan-sach"></a>

- **Method / Path:** `GET /budgets/summary`
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<BudgetSummaryRes>`):**
```json
{
  "success": true,
  "code": 200,
  "message": "Thành công",
  "data": {
    "total_limit": 10000000,
    "total_spent": 4500000,
    "total_remaining": 5500000,
    "spent_percentage": 45.0,
    "active_budgets_count": 3,
    "over_budget_count": 0
  }
}
```

---

### 3.2 Tạo mới ngân sách (`POST /budgets`) <a id="35-tao-ngan-sach"></a>

- **Method / Path:** `POST /budgets`
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`CreateBudgetRequest`):**
```json
{
  "name": "Ngân sách ăn uống tháng 10",
  "category_code": "an_uong",
  "amount_limit": 5000000,
  "period": "MONTH",
  "threshold_percentage": 80,
  "start_date": "2026-10-01",
  "end_date": "2026-10-31",
  "note": "Ngân sách chi tiêu cá nhân"
}
```
- **Ràng buộc:**
  - `category_code`: Phải tồn tại và chưa bị xóa.
  - Không trùng lặp ngân sách đang hoạt động cho cùng danh mục và cùng kỳ (`BUDGET_ALREADY_EXISTS`, HTTP 409).
- **Response Status:** `201 Created`

---

## 4. Bảng Ma trận Mã lỗi (Error Code Matrix) <a id="5-bang-ma-tran-ma-loi"></a>

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `BUDGET_ALREADY_EXISTS` | `409 Conflict` | Đã có ngân sách đang hoạt động cho danh mục này trong cùng kỳ |
| `BUDGET_CATEGORY_NOT_EDITABLE` | `400 Bad Request` | Không cho phép sửa danh mục hoặc kỳ ngân sách qua `PATCH` |
| `NOT_FOUND` | `404 Not Found` | Ngân sách không tồn tại hoặc không thuộc sở hữu của người dùng |
| `VALIDATION_ERROR` | `400 Bad Request` | Dữ liệu đầu vào không hợp lệ (hạn mức `<= 0`, ngày bắt đầu sau ngày kết thúc) |
