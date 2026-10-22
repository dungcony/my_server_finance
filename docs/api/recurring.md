# ĐẶC TẢ RESTFUL API ENDPOINTS — GIAO DỊCH ĐỊNH KỲ (RECURRING)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Giao Dịch Định Kỳ & Quản Lý Lịch Chạy (`/recurring`), cấu trúc Request/Response DTO, cơ chế tính toán mốc thời gian buffer, kích hoạt/bỏ qua buổi lịch (`skip`/`unskip`), và kịch bản kiểm thử API.  
> Code chính → [`RecController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/controller/RecController.java) · [`RecTxnBehavierImpl.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnBehavierImpl.java) · [`RecTxnScheduleBehavierImpl.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/transaction/service/impl/RecTxnScheduleBehavierImpl.java) · Luồng nghiệp vụ → [docs/work_flow/recurring.md](../work_flow/recurring.md)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Định kỳ (`/recurring`)](#3-dac-ta-chi-tiet-dinh-ky)
  - [3.1 Xem chi tiết định kỳ (`GET /recurring/{id}`)](#31-chi-tiet-dinh-ky)
  - [3.2 Tạo cấu hình định kỳ (`POST /recurring`)](#32-tao-dinh-ky)
  - [3.3 Cập nhật cấu hình định kỳ (`PATCH /recurring/{id}`)](#33-cap-nhat-dinh-ky)
  - [3.4 Bật / Tắt định kỳ (`enable` / `disable`)](#34-bat-tat-dinh-ky)
  - [3.5 Xóa mềm cấu hình định kỳ (`DELETE /recurring/{id}`)](#35-xoa-dinh-ky)
- [4. Đặc tả Chi tiết Buổi lịch con (`/recurring/{recId}/schedules`)](#4-dac-ta-chi-tiet-buoi-lich)
  - [4.1 Danh sách buổi lịch con (`GET /recurring/{recId}/schedules`)](#41-danh-sach-buoi-lich)
  - [4.2 Tạo buổi lịch riêng (`POST /recurring/{recId}/schedules`)](#42-tao-buoi-lich)
  - [4.3 Cập nhật buổi lịch (`PATCH /recurring/schedules/{scheduleId}`)](#43-cap-nhat-buoi-lich)
  - [4.4 Bỏ qua / Khôi phục buổi lịch (`skip` / `unskip`)](#44-skip-unskip)
  - [4.5 Xóa mềm buổi lịch (`DELETE /recurring/schedules/{scheduleId}`)](#45-xoa-buoi-lich)
- [5. Cấu trúc Thực thể JPA và DTO](#5-cau-truc-thuc-the-va-dto)
- [6. Biểu đồ Lớp Phân tầng (Spring Boot Class Diagram)](#6-bieu-do-lop-phan-tang)
- [7. Biểu đồ Tuần tự (Sequence Diagrams)](#7-bieu-do-tuan-tu)
- [8. Bảng Ma trận Mã lỗi (Error Code Matrix)](#8-bang-ma-tran-ma-loi)
- [9. Kịch bản Kiểm thử REST API (Spring Boot Test Cases)](#9-kich-ban-kiem-thu-rest-api)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/recurring`
- **Định dạng dữ liệu:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Bọc trong `ApiResponse<T>`.
- **Xác thực phiên:** `Authorization: Bearer <accessToken>`.
- **Cơ chế hoạt động:**
  - `recurring_transactions`: Chứa cấu hình lặp (ví, danh mục, loại, số tiền, tần suất `HOUR`, `DAY`, `WEEK`, `MONTH`, `QUARTER`, `YEAR`, bước lặp, mốc thời gian bắt đầu `start_at` và kết thúc `end_at`).
  - `recurring_transaction_schedules`: Chứa từng buổi lịch dự kiến sinh giao dịch với trạng thái: `PENDING`, `SUCCESS`, `FAILED`, `SKIPPED`.
  - Khi tạo hoặc thay đổi tần suất định kỳ, hệ thống duy trì sẵn buffer gồm **12 buổi lịch** gối đầu phía trước (nếu không có `end_at`).

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Quyền hạn | Mô tả |
| :--- | :--- | :--- | :--- |
| **`GET`** | `/recurring/{id}` | Đã đăng nhập | Xem chi tiết cấu hình định kỳ kèm thông tin ví và danh mục |
| **`POST`** | `/recurring` | Đã đăng nhập | Tạo cấu hình giao dịch định kỳ mới và tự động sinh các buổi lịch con |
| **`PATCH`** | `/recurring/{id}` | Đã đăng nhập | Cập nhật cấu hình định kỳ và đồng bộ lại các buổi lịch chưa chạy |
| **`POST`** | `/recurring/{id}/enable` | Đã đăng nhập | Kích hoạt lại định kỳ đã bị tắt (`is_enabled = true`) |
| **`POST`** | `/recurring/{id}/disable` | Đã đăng nhập | Tạm dừng định kỳ (`is_enabled = false`) |
| **`DELETE`** | `/recurring/{id}` | Đã đăng nhập | Xóa mềm cấu hình định kỳ |
| **`GET`** | `/recurring/{recId}/schedules` | Đã đăng nhập | Lấy danh sách các buổi lịch con của định kỳ (phân trang theo `run_date` tăng dần) |
| **`POST`** | `/recurring/{recId}/schedules` | Đã đăng nhập | Thêm thủ công một buổi lịch phát sinh riêng |
| **`PATCH`** | `/recurring/schedules/{scheduleId}` | Đã đăng nhập | Cập nhật ngày chạy, số tiền của một buổi lịch cụ thể |
| **`POST`** | `/recurring/schedules/{scheduleId}/skip` | Đã đăng nhập | Đánh dấu bỏ qua (`SKIPPED`) buổi lịch đó |
| **`POST`** | `/recurring/schedules/{scheduleId}/unskip`| Đã đăng nhập | Khôi phục buổi lịch bỏ qua về trạng thái `PENDING` |
| **`DELETE`** | `/recurring/schedules/{scheduleId}` | Đã đăng nhập | Xóa mềm một buổi lịch con |
| **`GET`** | `/recurring/admin/{id}` | `recurring_txn:read` | Admin xem chi tiết cấu hình định kỳ |
| **`GET`** | `/recurring/admin/{recId}/schedules` | `recurring_txn:read` | Admin xem danh sách lịch con của định kỳ |
| **`DELETE`** | `/recurring/admin/{id}` | `recurring_txn:delete` | Admin xóa cấu hình định kỳ |
| **`DELETE`** | `/recurring/admin/schedules/{scheduleId}` | `recurring_txn:delete` | Admin xóa buổi lịch con |

---

## 3. Đặc tả Chi tiết Endpoints Định kỳ (`/recurring`) <a id="3-dac-ta-chi-tiet-dinh-ky"></a>

### 3.1 Tạo cấu hình định kỳ (`POST /recurring`) <a id="32-tao-dinh-ky"></a>

- **Method / Path:** `POST /recurring`
- **Request Body (`RecCreateReq`):**
  - `wallet_id` (UUID, required): Ví phát sinh.
  - `category_code` (String, required): Danh mục lá hợp lệ.
  - `type` (TxnType, required): `EXPENSE` / `INCOME` / `LOAN`.
  - `amount` (Long, required): Số tiền mỗi lần phát sinh (`> 0`).
  - `frequency_type` (FrequencyType, required): `HOUR`, `DAY`, `WEEK`, `MONTH`, `QUARTER`, `YEAR`.
  - `frequency_interval` (Integer, required): Bước nhảy lặp (ví dụ `1` tháng 1 lần, `2` tuần 1 lần).
  - `start_at` (Instant, required): Thời điểm bắt đầu hiệu lực.
  - `end_at` (Instant, optional): Thời điểm kết thúc (nếu có phải ở tương lai).
  - `name` (String): Tên định kỳ.
  - `note` (String): Ghi chú.
- **Hành vi:**
  - Kiểm tra ví thuộc user, danh mục tồn tại và là nút lá.
  - Lưu vào bảng `recurring_transactions`.
  - Tự động sinh sẵn các buổi lịch ban đầu vào `recurring_transaction_schedules` với trạng thái `PENDING`.
- **Response Status:** `201 Created`

---

### 3.2 Bỏ qua / Khôi phục buổi lịch (`skip` / `unskip`) <a id="44-skip-unskip"></a>

- **Skip:** `POST /recurring/schedules/{scheduleId}/skip` ➔ Chuyển trạng thái buổi lịch thành `SKIPPED`. Background scheduler khi quét đến sẽ bỏ qua không sinh giao dịch cho buổi này.
- **Unskip:** `POST /recurring/schedules/{scheduleId}/unskip` ➔ Chuyển trạng thái từ `SKIPPED` trở lại `PENDING` nếu thời điểm chạy chưa qua.

---

## 4. Kịch bản Kiểm thử REST API (Spring Boot Test Cases) <a id="9-kich-ban-kiem-thu-rest-api"></a>

| Mã Test Case | Loại test | Mô tả kịch bản | Đầu vào HTTP Request | Kỳ vọng |
| :--- | :--- | :--- | :--- | :--- |
| **TC_REC_01** | Luồng chuẩn | Tạo cấu hình định kỳ hàng tháng | `POST /recurring`<br>Body: amount=500k, frequency=MONTH, interval=1 | `201 Created`, sinh 12 buổi lịch `PENDING` |
| **TC_REC_02** | Validation | Báo lỗi khi danh mục không phải lá | `POST /recurring`<br>Body: category_code=`an_uong` (cha) | `400 Bad Request` |
| **TC_REC_03** | Bật / Tắt | Disable định kỳ thành công | `POST /recurring/{id}/disable` | `200 OK`, `is_enabled=false` |
| **TC_REC_04** | Buổi lịch | Bỏ qua buổi lịch (skip) | `POST /recurring/schedules/{id}/skip` | `200 OK`, trạng thái thành `SKIPPED` |
| **TC_REC_05** | Đồng bộ sửa | Sửa số tiền định kỳ cập nhật lịch con | `PATCH /recurring/{id}`<br>Body: amount=700k | `200 OK`, các buổi `PENDING` cập nhật amount=700k |
