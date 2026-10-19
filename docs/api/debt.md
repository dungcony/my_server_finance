# ĐẶC TẢ RESTFUL API ENDPOINTS — QUẢN LÝ SỔ NỢ (DEBT)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Quản Lý Sổ Nợ (`/debts`), đợt thanh toán nợ (`payments`), xử lý nợ khó đòi (`write-off`), phân hệ Admin đối soát, và kịch bản kiểm thử API.  
> Code chính → [`DebtController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/debt/controller/DebtController.java) · [`DebtService.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/debt/service/DebtService.java)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Người dùng](#3-dac-ta-chi-tiet-endpoints-nguoi-dung)
  - [3.1 Danh sách khoản nợ (`GET /debts`)](#31-danh-sach-no)
  - [3.2 Báo cáo tổng quan sổ nợ (`GET /debts/summary`)](#32-tong-quan-so-no)
  - [3.3 Chi tiết khoản nợ (`GET /debts/{id}`)](#33-chi-tiet-khoan-no)
  - [3.4 Tạo mới khoản nợ (`POST /debts`)](#34-tao-khoan-no)
  - [3.5 Ghi nhận đợt thanh toán (`POST /debts/{id}/payments`)](#35-thanh-toan-no)
  - [3.6 Hủy đợt thanh toán (`DELETE /debts/{id}/payments/{paymentId}`)](#36-huy-thanh-toan)
  - [3.7 Xóa nợ khó đòi (`POST /debts/{id}/write-off`)](#37-xoa-no-kho-doi)
  - [3.8 Xóa khoản nợ (`DELETE /debts/{id}`)](#38-xoa-khoan-no)
- [4. Đặc tả Chi tiết Endpoints Quản trị viên (`/debts/admin`)](#4-dac-ta-chi-tiet-endpoints-admin)
- [5. Bảng Ma trận Mã lỗi (Error Code Matrix)](#5-bang-ma-tran-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/debts`
- **Định dạng:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Xác thực:** Header `Authorization: Bearer <accessToken>`.
- **Phân loại nợ (`DebtType`):**
  - `LENT` (Cho vay): Người dùng cho đối tác vay tiền (tài sản cần thu hồi).
  - `BORROWED` (Đi vay): Người dùng vay tiền từ đối tác (nghĩa vụ phải trả).
- **Trạng thái nợ:** `OUTSTANDING` (Còn nợ), `SETTLED` (Đã tất toán hết), `WRITTEN_OFF` (Xóa nợ khó đòi).

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Quyền hạn | Idempotent | Mô tả |
| :--- | :--- | :--- | :---: | :--- |
| **`GET`** | `/debts` | Đã đăng nhập | Không | Danh sách các khoản nợ của người dùng theo trạng thái và đối tác |
| **`GET`** | `/debts/summary` | Đã đăng nhập | Không | Tổng quan: tổng tiền cho vay, tổng tiền đi vay, số khoản quá hạn |
| **`GET`** | `/debts/{id}` | Đã đăng nhập | Không | Xem chi tiết khoản nợ kèm danh sách các đợt trả tiền |
| **`POST`** | `/debts` | Đã đăng nhập | **Có** | Tạo khoản nợ mới (tự động phát sinh giao dịch ví liên kết nếu chọn ví) |
| **`PATCH`** | `/debts/{id}` | Đã đăng nhập | Không | Cập nhật thông tin đối tác, hạn trả, lãi suất, ghi chú |
| **`POST`** | `/debts/{id}/payments` | Đã đăng nhập | **Có** | Ghi nhận một đợt trả nợ / thu hồi nợ |
| **`DELETE`** | `/debts/{id}/payments/{pId}`| Đã đăng nhập | Không | Hủy một đợt trả nợ đã ghi nhận |
| **`POST`** | `/debts/{id}/write-off` | Đã đăng nhập | Không | Đánh dấu xóa nợ khó đòi (không thể thu hồi) |
| **`DELETE`** | `/debts/{id}` | Đã đăng nhập | Không | Xóa khoản nợ |
| **`GET`** | `/debts/admin` | `debts:read` | Không | Admin tra cứu sổ nợ toàn hệ thống hoặc theo `user_id` |
| **`GET`** | `/debts/admin/summary` | `debts:read` | Không | Admin xem tổng quan nợ toàn hệ thống |

---

## 3. Đặc tả Chi tiết Endpoints Người dùng <a id="3-dac-ta-chi-tiet-endpoints-nguoi-dung"></a>

### 3.1 Báo cáo tổng quan sổ nợ (`GET /debts/summary`) <a id="32-tong-quan-so-no"></a>

- **Method / Path:** `GET /debts/summary`
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<DebtSummaryResponse>`):**
```json
{
  "success": true,
  "code": 200,
  "message": "Thành công",
  "data": {
    "total_lent": 10000000,
    "total_lent_remaining": 6000000,
    "total_borrowed": 5000000,
    "total_borrowed_remaining": 2000000,
    "overdue_count": 1
  }
}
```

---

### 3.2 Ghi nhận đợt trả nợ (`POST /debts/{id}/payments`) <a id="35-thanh-toan-no"></a>

- **Method / Path:** `POST /debts/{id}/payments`
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`CreatePaymentRequest`):**
```json
{
  "amount": 2000000,
  "paid_at": "2026-10-19T10:00:00Z",
  "wallet_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "note": "Trả đợt 1 chuyển khoản"
}
```
- **Hành vi:**
  - Cập nhật số tiền còn lại của khoản nợ (`remaining_amount = remaining_amount - amount`).
  - Nếu `remaining_amount == 0`: Khoản nợ tự động đổi trạng thái sang `SETTLED`.
  - Tự động sinh giao dịch tài chính tương ứng vào ví chỉ định.
- **Response Status:** `201 Created`

---

## 4. Bảng Ma trận Mã lỗi (Error Code Matrix) <a id="5-bang-ma-tran-ma-loi"></a>

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `DEBT_AMOUNT_EXCEEDED` | `400 Bad Request` | Số tiền trả nợ vượt quá số dư nợ còn lại |
| `DEBT_ALREADY_SETTLED` | `400 Bad Request` | Khoản nợ đã được tất toán xong, không thể thanh toán thêm |
| `NOT_FOUND` | `404 Not Found` | Khoản nợ hoặc đợt thanh toán không tồn tại |
| `VALIDATION_ERROR` | `400 Bad Request` | Số tiền `<= 0` hoặc ví không hợp lệ |
