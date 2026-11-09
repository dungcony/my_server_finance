# ĐẶC TẢ RESTFUL API ENDPOINTS — QUẢN LÝ SỔ NỢ (DEBT)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Quản Lý Sổ Nợ
> (`/debts`), các đợt thanh toán nợ (`/debts/{debtId}/payments`), phân hệ Admin đối soát, và kịch bản kiểm thử API.  
> Code chính → `DebtController.java` · `DebtPaymentController.java` · `DebtAdminController.java` · `DebtPaymentAdminController.java`

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Người dùng](#3-dac-ta-chi-tiet-endpoints-nguoi-dung)
    - [3.1 Danh sách khoản nợ (`GET /debts`)](#31-danh-sach-no)
    - [3.2 Chi tiết khoản nợ (`GET /debts/{id}`)](#32-chi-tiet-khoan-no)
    - [3.3 Tạo mới khoản nợ (`POST /debts`)](#33-tao-khoan-no)
    - [3.4 Cập nhật khoản nợ (`PATCH /debts/{id}`)](#34-cap-nhat-khoan-no)
    - [3.5 Xóa khoản nợ (`DELETE /debts/{id}`)](#35-xoa-khoan-no)
    - [3.6 Danh sách đợt thanh toán (`GET /debts/{debtId}/payments`)](#36-danh-sach-thanh-toan)
    - [3.7 Ghi nhận đợt thanh toán (`POST /debts/{debtId}/payments`)](#37-thanh-toan-no)
    - [3.8 Cập nhật đợt thanh toán (`PATCH /debts/{debtId}/payments/{paymentId}`)](#38-cap-nhat-thanh-toan)
    - [3.9 Hủy đợt thanh toán (`DELETE /debts/{debtId}/payments/{paymentId}`)](#39-huy-thanh-toan)
- [4. Đặc tả Chi tiết Endpoints Quản trị viên (`/admin/debts`)](#4-dac-ta-chi-tiet-endpoints-admin)
- [5. Bảng Ma trận Mã lỗi (Error Code Matrix)](#5-bang-ma-tran-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/debts` (User) và `/admin/debts` (Admin)
- **Định dạng:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Xác thực:** Header `Authorization: Bearer <accessToken>`.
- **Phân loại nợ (`DebtType`):**
    - `LENDING` (Cho vay): Người dùng cho đối tác vay tiền (tài sản cần thu hồi, ví bị trừ tiền khi tạo nợ).
    - `BORROWING` (Đi vay): Người dùng vay tiền từ đối tác (nghĩa vụ phải trả, ví được cộng tiền khi tạo nợ).
- **Trạng thái nợ (`DebtStatus`):**
    - `INPROCESS` (Đang thực hiện / Chưa tất toán hết).
    - `COMPLETED` (Đã hoàn tất / Đã tất toán đủ).
- **Quy tắc dòng tiền và tính toàn vẹn:**
    - `DebtPayment` là giao dịch độc lập (không nằm trong bảng `transactions`).
    - Số dư ví liên kết được trigger DB tự động cộng/trừ trực tiếp dựa trên net effect của `total_amount` và `paid_amount`.
    - Không cho phép xóa khoản nợ khi đang ở trạng thái `INPROCESS`.

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

### 2.1 Endpoints Người dùng (`/debts`)

| Phương thức  | Endpoint                                  | Quyền hạn    | Idempotent | Mô tả |
|:-------------|:------------------------------------------|:-------------|:----------:|:------|
| **`GET`**    | `/debts`                                  | Đã đăng nhập |   Không    | Lấy danh sách khoản nợ của người dùng theo bộ lọc và phân trang |
| **`GET`**    | `/debts/{id}`                             | Đã đăng nhập |   Không    | Xem chi tiết một khoản nợ |
| **`POST`**   | `/debts`                                  | Đã đăng nhập |   **Có**   | Tạo khoản nợ mới (trigger tự động cộng/trừ số dư ví liên kết) |
| **`PATCH`**  | `/debts/{id}`                             | Đã đăng nhập |   Không    | Cập nhật tên, hạn trả, ghi chú của khoản nợ |
| **`DELETE`** | `/debts/{id}`                             | Đã đăng nhập |   Không    | Xóa mềm khoản nợ (chỉ cho phép khi đã `COMPLETED`) |
| **`GET`**    | `/debts/{debtId}/payments`                | Đã đăng nhập |   Không    | Danh sách lịch sử các đợt thanh toán của khoản nợ có phân trang |
| **`POST`**   | `/debts/{debtId}/payments`                | Đã đăng nhập |   **Có**   | Ghi nhận một đợt trả nợ / thu hồi nợ (trigger tự cập nhật ví và nợ) |
| **`PATCH`**  | `/debts/{debtId}/payments/{paymentId}`    | Đã đăng nhập |   Không    | Sửa số tiền, ngày trả, ghi chú của đợt thanh toán |
| **`DELETE`** | `/debts/{debtId}/payments/{paymentId}`    | Đã đăng nhập |   Không    | Hủy (xóa mềm) đợt thanh toán, trigger tự hoàn tác tiền ví |

### 2.2 Endpoints Quản trị viên (`/admin/debts`)

| Phương thức  | Endpoint                                        | Quyền hạn      | Idempotent | Mô tả |
|:-------------|:------------------------------------------------|:---------------|:----------:|:------|
| **`GET`**    | `/admin/debts/{userId}`                         | `DEBT_READ`    |   Không    | Xem danh sách khoản nợ của một user chỉ định |
| **`GET`**    | `/admin/debts/{id}`                             | `DEBT_READ`    |   Không    | Xem chi tiết một khoản nợ bất kỳ |
| **`POST`**   | `/admin/debts?user_id={userId}`                 | `DEBT_UPDATE`  |   **Có**   | Admin tạo khoản nợ thay cho user |
| **`PATCH`**  | `/admin/debts/{id}`                             | `DEBT_UPDATE`  |   Không    | Admin sửa khoản nợ |
| **`DELETE`** | `/admin/debts/{id}`                             | `DEBT_DELETE`  |   Không    | Admin xóa khoản nợ |
| **`GET`**    | `/admin/debts/{debtId}/payments`                | `DEBT_READ`    |   Không    | Admin xem danh sách đợt thanh toán |
| **`POST`**   | `/admin/debts/{debtId}/payments`                | `DEBT_UPDATE`  |   **Có**   | Admin tạo đợt thanh toán |
| **`PATCH`**  | `/admin/debts/{debtId}/payments/{paymentId}`    | `DEBT_UPDATE`  |   Không    | Admin sửa đợt thanh toán |
| **`DELETE`** | `/admin/debts/{debtId}/payments/{paymentId}`    | `DEBT_DELETE`  |   Không    | Admin xóa đợt thanh toán |

---

## 3. Đặc tả Chi tiết Endpoints Người dùng <a id="3-dac-ta-chi-tiet-endpoints-nguoi-dung"></a>

### 3.1 Danh sách khoản nợ (`GET /debts`) <a id="31-danh-sach-no"></a>

- **Method / Path:** `GET /debts`
- **Query Params (`DebtFilterReq`):**
    - `name` (String): Tìm kiếm theo tên / đối tác
    - `type` (`LENDING`, `BORROWING`): Loại nợ
    - `status` (`INPROCESS`, `COMPLETED`): Trạng thái nợ
    - `wallet_id` (UUID): Ví liên kết
    - `issued_date_from`, `issued_date_to` (YYYY-MM-DD): Khoảng ngày tạo nợ
    - `due_date_from`, `due_date_to` (YYYY-MM-DD): Khoảng hạn trả
    - `is_overdue` (Boolean): Lọc khoản nợ quá hạn
    - `min_amount`, `max_amount` (Long): Khoảng tiền nợ
    - `page` (int, default 0), `size` (int, default 20)
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<DebtListRes>`):**

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "list": [
      {
        "id": "c1f7b8a2-9b2e-4e5a-8b1c-3d4e5f6a7b8c",
        "wallet_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
        "user_id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
        "name": "Nguyễn Văn A",
        "type": "LENDING",
        "status": "INPROCESS",
        "total_amount": 5000000,
        "paid_amount": 2000000,
        "issued_date": "2026-10-01",
        "due_date": "2026-11-01",
        "note": "Cho vay tiền mua laptop"
      }
    ],
    "page_meta": {
      "page": 0,
      "size": 20,
      "total_elements": 1,
      "total_pages": 1
    }
  }
}
```

---

### 3.2 Chi tiết khoản nợ (`GET /debts/{id}`) <a id="32-chi-tiet-khoan-no"></a>

- **Method / Path:** `GET /debts/{id}`
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<DebtRes>`):**

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "id": "c1f7b8a2-9b2e-4e5a-8b1c-3d4e5f6a7b8c",
    "wallet_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
    "user_id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
    "name": "Nguyễn Văn A",
    "type": "LENDING",
    "status": "INPROCESS",
    "total_amount": 5000000,
    "paid_amount": 2000000,
    "issued_date": "2026-10-01",
    "due_date": "2026-11-01",
    "note": "Cho vay tiền mua laptop"
  }
}
```

---

### 3.3 Tạo mới khoản nợ (`POST /debts`) <a id="33-tao-khoan-no"></a>

- **Method / Path:** `POST /debts`
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`DebtCreateReq`):**

```json
{
  "wallet_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "name": "Nguyễn Văn A",
  "type": "LENDING",
  "total_amount": 5000000,
  "issued_date": "2026-10-01",
  "due_date": "2026-11-01",
  "note": "Cho vay tiền mua laptop"
}
```

- **Hành vi:**
    - Khởi tạo khoản nợ với `status = INPROCESS`, `paid_amount = 0`.
    - Trigger DB `trg_debts_wallet_balance` tự động cập nhật số dư ví:
        - `LENDING` $\rightarrow$ Ví trừ 5,000,000đ.
        - `BORROWING` $\rightarrow$ Ví cộng 5,000,000đ.
- **Response Status:** `201 Created`

---

### 3.4 Cập nhật khoản nợ (`PATCH /debts/{id}`) <a id="34-cap-nhat-khoan-no"></a>

- **Method / Path:** `PATCH /debts/{id}`
- **Request Body (`DebtUpdateReq`):**

```json
{
  "name": "Nguyễn Văn A (bạn đại học)",
  "due_date": "2026-11-15",
  "note": "Gia hạn thêm 2 tuần"
}
```

- **Response Status:** `200 OK`

---

### 3.5 Xóa khoản nợ (`DELETE /debts/{id}`) <a id="35-xoa-khoan-no"></a>

- **Method / Path:** `DELETE /debts/{id}`
- **Hành vi:**
    - Chỉ cho phép xóa khi `status = COMPLETED`.
    - Nếu đang `INPROCESS`, hệ thống từ chối xóa với lỗi `CONFLICT` (HTTP 409).
- **Response Status:** `200 OK` (Body `null`)

---

### 3.6 Danh sách đợt thanh toán (`GET /debts/{debtId}/payments`) <a id="36-danh-sach-thanh-toan"></a>

- **Method / Path:** `GET /debts/{debtId}/payments`
- **Query Params (`DebtPaymentFilterReq`):**
    - `paid_date_from`, `paid_date_to` (YYYY-MM-DD): Khoảng ngày thanh toán
    - `min_amount`, `max_amount` (Long): Khoảng số tiền thanh toán
    - `note` (String): Tìm trong ghi chú
    - `page` (int), `size` (int)
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<DebtPaymentListRes>`):**

```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "list": [
      {
        "id": "e4f5a6b7-c8d9-0e1f-2a3b-4c5d6e7f8a9b",
        "debt_id": "c1f7b8a2-9b2e-4e5a-8b1c-3d4e5f6a7b8c",
        "amount": 2000000,
        "paid_date": "2026-10-15",
        "note": "Trả đợt 1 tiền mặt",
        "created_at": "2026-10-15T14:30:00Z"
      }
    ],
    "page_meta": {
      "page": 0,
      "size": 20,
      "total_elements": 1,
      "total_pages": 1
    }
  }
}
```

---

### 3.7 Ghi nhận đợt thanh toán (`POST /debts/{debtId}/payments`) <a id="37-thanh-toan-no"></a>

- **Method / Path:** `POST /debts/{debtId}/payments`
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`DebtPaymentCreateReq`):**

```json
{
  "amount": 2000000,
  "paid_date": "2026-10-15",
  "note": "Trả đợt 1 tiền mặt"
}
```

- **Hành vi:**
    - Kiểm tra số tiền trả không vượt quá số nợ còn lại (`total_amount - paid_amount`).
    - Trigger DB `trg_debt_payments_sync` cộng dồn `paid_amount` của khoản nợ cha.
    - Nếu trả đủ (`paid_amount >= total_amount`), trạng thái khoản nợ tự động đổi sang `COMPLETED`.
    - Trigger DB `trg_debts_wallet_balance` tự động cập nhật số dư ví:
        - `LENDING` (Thu nợ) $\rightarrow$ Ví cộng 2,000,000đ.
        - `BORROWING` (Trả nợ) $\rightarrow$ Ví trừ 2,000,000đ.
    - Kích hoạt event gửi thông báo `DebtCompletedEvent` nếu khoản nợ hoàn thành.
- **Response Status:** `201 Created`

---

### 3.8 Cập nhật đợt thanh toán (`PATCH /debts/{debtId}/payments/{paymentId}`) <a id="38-cap-nhat-thanh-toan"></a>

- **Method / Path:** `PATCH /debts/{debtId}/payments/{paymentId}`
- **Request Body (`DebtPaymentUpdateReq`):**

```json
{
  "amount": 3000000,
  "paid_date": "2026-10-16",
  "note": "Cập nhật lại số tiền đợt 1"
}
```

- **Hành vi:**
    - Trigger DB tự động tính lại phần chênh lệch `paid_amount` và điều chỉnh lại số dư ví tương ứng.
- **Response Status:** `200 OK`

---

### 3.9 Hủy đợt thanh toán (`DELETE /debts/{debtId}/payments/{paymentId}`) <a id="39-huy-thanh-toan"></a>

- **Method / Path:** `DELETE /debts/{debtId}/payments/{paymentId}`
- **Hành vi:**
    - Xóa mềm đợt thanh toán (`is_deleted = true`).
    - Trigger DB tự động trừ lại `paid_amount`, nếu khoản nợ đang `COMPLETED` sẽ được tự động mở lại thành `INPROCESS`.
    - Trigger DB tự động hoàn tác tiền trong ví.
- **Response Status:** `200 OK` (Body `null`)

---

## 4. Đặc tả Chi tiết Endpoints Quản trị viên (`/admin/debts`) <a id="4-dac-ta-chi-tiet-endpoints-admin"></a>

Dành cho Admin quản lý sổ nợ toàn hệ thống:
- `GET /admin/debts/{userId}`: Xem danh sách nợ của user (`DEBT_READ`).
- `GET /admin/debts/{id}`: Xem chi tiết nợ (`DEBT_READ`).
- `POST /admin/debts?user_id={userId}`: Admin tạo nợ (`DEBT_UPDATE`).
- `PATCH /admin/debts/{id}`: Admin sửa nợ (`DEBT_UPDATE`).
- `DELETE /admin/debts/{id}`: Admin xóa nợ (`DEBT_DELETE`).
- `GET /admin/debts/{debtId}/payments`: Admin xem danh sách thanh toán (`DEBT_READ`).
- `POST /admin/debts/{debtId}/payments`: Admin tạo thanh toán (`DEBT_UPDATE`).
- `PATCH /admin/debts/{debtId}/payments/{paymentId}`: Admin sửa thanh toán (`DEBT_UPDATE`).
- `DELETE /admin/debts/{debtId}/payments/{paymentId}`: Admin xóa thanh toán (`DEBT_DELETE`).

---

## 5. Bảng Ma trận Mã lỗi (Error Code Matrix) <a id="5-bang-ma-tran-ma-loi"></a>

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `DEBT_NOT_FOUND` | `404 Not Found` | Không tìm thấy khoản nợ hoặc không thuộc quyền sở hữu |
| `DEBT_IS_COMPLETED` | `400 Bad Request` | Khoản nợ đã tất toán xong, không thể tạo thêm đợt thanh toán |
| `DEBT_DUE_DATE_INVALID` | `400 Bad Request` | Hạn trả nợ (`due_date`) không được trước ngày tạo nợ (`issued_date`) |
| `CONFLICT` | `409 Conflict` | Không thể xóa khoản nợ khi đang `INPROCESS` hoặc số tiền trả vượt quá nợ còn lại |
| `VALIDATION_ERROR` | `400 Bad Request` | Dữ liệu đầu vào không hợp lệ (số tiền `<= 0`, ngày trả trong tương lai...) |
