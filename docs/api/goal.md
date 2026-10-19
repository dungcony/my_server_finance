# ĐẶC TẢ RESTFUL API ENDPOINTS — MỤC TIÊU TIẾT KIỆM (GOAL)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Mục Tiêu Tiết Kiệm (`/goals`), cơ chế tự động sinh ví tiết kiệm (`SAVINGS`), nạp tiền tích lũy (`contributions`), hoàn tiền khi hủy mục tiêu, và kịch bản kiểm thử API.  
> Code chính → [`GoalController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/goal/controller/GoalController.java) · [`GoalService.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/goal/service/GoalService.java)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Người dùng](#3-dac-ta-chi-tiet-endpoints-nguoi-dung)
  - [3.1 Danh sách mục tiêu (`GET /goals`)](#31-danh-sach-muc-tieu)
  - [3.2 Chi tiết mục tiêu (`GET /goals/{id}`)](#32-chi-tiet-muc-tieu)
  - [3.3 Tạo mục tiêu tiết kiệm mới (`POST /goals`)](#33-tao-muc-tieu)
  - [3.4 Cập nhật mục tiêu (`PATCH /goals/{id}`)](#34-cap-nhat-muc-tieu)
  - [3.5 Nạp tiền vào mục tiêu (`POST /goals/{id}/contributions`)](#35-nap-tien-muc-tieu)
  - [3.6 Hủy đợt đóng góp (`DELETE /goals/{id}/contributions/{cId}`)](#36-huy-dong-gop)
  - [3.7 Xóa mục tiêu tiết kiệm (`DELETE /goals/{id}`)](#37-xoa-muc-tieu)
- [4. Đặc tả Chi tiết Endpoints Quản trị viên (`/goals/admin`)](#4-dac-ta-chi-tiet-endpoints-admin)
- [5. Bảng Ma trận Mã lỗi (Error Code Matrix)](#5-bang-ma-tran-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/goals`
- **Định dạng dữ liệu:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Cơ chế liên kết ví SAVINGS:** Mỗi mục tiêu tiết kiệm khi được tạo thành công sẽ tự động tạo một ví phụ chuyên dụng có kiểu `SAVINGS` thuộc quyền sở hữu của người dùng đó. Mọi khoản đóng góp (`contributions`) sẽ chuyển tiền từ ví nguồn của người dùng vào ví `SAVINGS` tương ứng.

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Quyền hạn | Idempotent | Mô tả |
| :--- | :--- | :--- | :---: | :--- |
| **`GET`** | `/goals` | Đã đăng nhập | Không | Danh sách mục tiêu tiết kiệm của người dùng (lọc theo trạng thái `ACTIVE`, `COMPLETED`, `CANCELLED`) |
| **`GET`** | `/goals/{id}` | Đã đăng nhập | Không | Xem chi tiết mục tiêu, tiến độ tích lũy %, lịch sử các lần nạp tiền |
| **`POST`** | `/goals` | Đã đăng nhập | **Có** | Tạo mục tiêu mới (hệ thống tự động khởi tạo ví `SAVINGS`) |
| **`PATCH`** | `/goals/{id}` | Đã đăng nhập | Không | Cập nhật tên mục tiêu, số tiền mục tiêu, ngày hết hạn |
| **`DELETE`** | `/goals/{id}` | Đã đăng nhập | Không | Xóa mục tiêu (tùy chọn hoàn tiền về ví gốc) |
| **`POST`** | `/goals/{id}/contributions` | Đã đăng nhập | **Có** | Nạp tiền tích lũy từ ví thanh toán vào mục tiêu |
| **`DELETE`** | `/goals/{id}/contributions/{cId}` | Đã đăng nhập | Không | Hủy lượt nạp tiền và hoàn tiền về ví nguồn |
| **`GET`** | `/goals/admin/all` | `goal:read` | Không | Admin tra cứu mục tiêu toàn hệ thống |
| **`GET`** | `/goals/admin/detail/{id}` | `goal:read` | Không | Admin xem chi tiết mục tiêu bất kỳ |
| **`DELETE`** | `/goals/admin/{id}` | `goal:delete` | Không | Admin cưỡng chế xóa mục tiêu |

---

## 3. Đặc tả Chi tiết Endpoints Người dùng <a id="3-dac-ta-chi-tiet-endpoints-nguoi-dung"></a>

### 3.1 Tạo mục tiêu mới (`POST /goals`) <a id="33-tao-muc-tieu"></a>

- **Method / Path:** `POST /goals`
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`CreateGoalRequest`):**
```json
{
  "name": "Mua MacBook Pro M4",
  "target_amount": 50000000,
  "target_date": "2026-12-31",
  "icon_code": "dien_thoai",
  "color": "#3a6ea5",
  "initial_amount": 5000000,
  "source_wallet_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e"
}
```
- **Hành vi:**
  - Tự động tạo một ví `SAVINGS` mới có tên tương ứng mục tiêu.
  - Nếu có `initial_amount > 0`: Tự động trích tiền từ `source_wallet_id` chuyển vào ví `SAVINGS`.
- **Response Status:** `201 Created`

---

### 3.2 Nạp tiền tích lũy (`POST /goals/{id}/contributions`) <a id="35-nap-tien-muc-tieu"></a>

- **Method / Path:** `POST /goals/{id}/contributions`
- **Request Body (`CreateContributionRequest`):**
```json
{
  "amount": 2000000,
  "source_wallet_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "note": "Trích lương tháng 10"
}
```
- **Hành vi:**
  - Trừ tiền từ ví nguồn và cộng tiền vào ví `SAVINGS` của mục tiêu.
  - Cập nhật số tiền hiện có (`current_amount`).
  - Nếu `current_amount >= target_amount`: Mục tiêu tự động hoàn thành (`status = COMPLETED`).
- **Response Status:** `201 Created`

---

## 4. Bảng Ma trận Mã lỗi (Error Code Matrix) <a id="5-bang-ma-tran-ma-loi"></a>

| Mã lỗi | HTTP Status | Mô tả |
| :--- | :--- | :--- |
| `GOAL_ALREADY_COMPLETED` | `400 Bad Request` | Mục tiêu đã hoàn thành, không thể nạp thêm tiền |
| `WALLET_BALANCE_INSUFFICIENT` | `422 Unprocessable Entity` | Số dư ví nguồn không đủ để nạp tiền vào mục tiêu |
| `NOT_FOUND` | `404 Not Found` | Mục tiêu hoặc ví nguồn không tồn tại |
| `VALIDATION_ERROR` | `400 Bad Request` | Số tiền mục tiêu `<= 0`, ngày đích ở quá khứ |
