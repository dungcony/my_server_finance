# ĐẶC TẢ RESTFUL API ENDPOINTS — MỤC TIÊU TIẾT KIỆM (GOAL)

> **File này trả lời:** Danh sách và đặc tả kỹ thuật chi tiết các RESTful API endpoints phân hệ Mục Tiêu Tiết Kiệm
> (`/goals`, `/admin/goals`), theo dõi tiến độ tích lũy tài chính độc lập (không gắn ví, không sinh transaction), nạp tiền,
> điều chỉnh đợt đóng góp, phân trang lịch sử và các quyền quản trị Admin.  
> Code chính →
> [SavingGoalController.java](file:///d:/projects/java/server/src/main/java/com/datn/financeapp/module/goal/controller/SavingGoalController.java) ·
> [ContributionGoalController.java](file:///d:/projects/java/server/src/main/java/com/datn/financeapp/module/goal/controller/ContributionGoalController.java) ·
> [SavingGoalAdminController.java](file:///d:/projects/java/server/src/main/java/com/datn/financeapp/module/goal/controller/SavingGoalAdminController.java) ·
> [ContributionGoalAdminController.java](file:///d:/projects/java/server/src/main/java/com/datn/financeapp/module/goal/controller/ContributionGoalAdminController.java)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Người dùng](#3-dac-ta-chi-tiet-endpoints-nguoi-dung)
    - [3.1 Danh sách mục tiêu (`GET /goals`)](#31-danh-sach-muc-tieu)
    - [3.2 Chi tiết mục tiêu (`GET /goals/{id}`)](#32-chi-tiet-muc-tieu)
    - [3.3 Tạo mục tiêu tiết kiệm mới (`POST /goals`)](#33-tao-muc-tieu)
    - [3.4 Cập nhật mục tiêu (`PATCH /goals/{id}`)](#34-cap-nhat-muc-tieu)
    - [3.5 Xóa mục tiêu tiết kiệm (`DELETE /goals/{id}`)](#35-xoa-muc-tieu)
    - [3.6 Nộp tiền vào mục tiêu (`POST /goals/{id}/contributions`)](#36-nap-tien-muc-tieu)
    - [3.7 Danh sách lịch sử nạp/rút tiền (`GET /goals/{id}/contributions`)](#37-danh-sach-dong-gop)
    - [3.8 Sửa khoản đóng góp (`PATCH /goals/{id}/contributions/{contributionId}`)](#38-sua-khoan-dong-gop)
    - [3.9 Xóa đợt đóng góp (`DELETE /goals/{id}/contributions/{contributionId}`)](#39-xoa-khoan-dong-gop)
- [4. Đặc tả Chi tiết Endpoints Quản trị viên (Admin)](#4-dac-ta-chi-tiet-endpoints-admin)
    - [4.1 Quản lý mục tiêu (`/admin/goals`)](#41-quan-ly-muc-tieu-admin)
    - [4.2 Quản lý đóng góp (`/admin/goals/{id}/contributions`)](#42-quan-ly-dong-gop-admin)
- [5. Bảng Ma trận Mã lỗi (Error Code Matrix)](#5-bang-ma-tran-ma-loi)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:**
    - Người dùng: `/goals` và `/goals/{id}`
    - Quản trị viên: `/admin/goals` và `/admin/goals/{id}`
- **Định dạng dữ liệu:** `Content-Type: application/json`, Jackson cấu hình toàn cục `snake_case`.
- **Đóng gói chuẩn:** Toàn bộ phản hồi thành công bọc trong `ApiResponse<T>`.
- **Cơ chế Goal Tracker độc lập:** Mục tiêu tiết kiệm hoạt động như một công cụ theo dõi tiến độ tích lũy thực tế (như
  nuôi heo đất, sổ tiết kiệm ngân hàng, tiền mặt trong tủ...), không gắn với bất kỳ ví nào trong hệ thống và không tự động sinh
  giao dịch chi tiêu/thu nhập.
- **Tự động đồng bộ số dư qua Database Trigger:** Mọi biến động tăng/giảm số tiền tích lũy và cập nhật trạng thái mục tiêu
  (`in_progress`, `completed`) đều được kích hoạt tự động qua Trigger CSDL `trg_goal_contributions_sync` khi có thay đổi trên bảng `goal_contributions`.

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức  | Endpoint                                           | Quyền hạn     | Idempotent | Mô tả                                                           |
|:-------------|:---------------------------------------------------|:--------------|:----------:|:----------------------------------------------------------------|
| **`GET`**    | `/goals`                                           | Đã đăng nhập  |   Không    | Danh sách mục tiêu phân trang của user (lọc theo tên, hạn, status) |
| **`GET`**    | `/goals/{id}`                                      | Đã đăng nhập  |   Không    | Xem chi tiết một mục tiêu tiết kiệm                             |
| **`POST`**   | `/goals`                                           | Đã đăng nhập  |   **Có**   | Tạo mục tiêu mới (tùy chọn nạp số tiền ban đầu `initial_amount`) |
| **`PATCH`**  | `/goals/{id}`                                      | Đã đăng nhập  |   Không    | Cập nhật tên, hạn chót, số tiền mục tiêu, biểu tượng, ghi chú   |
| **`DELETE`** | `/goals/{id}`                                      | Đã đăng nhập  |   Không    | Xóa mềm mục tiêu và toàn bộ đóng góp trực thuộc                 |
| **`POST`**   | `/goals/{id}/contributions`                        | Đã đăng nhập  |   **Có**   | Nạp tiền vào mục tiêu tiết kiệm                                 |
| **`GET`**    | `/goals/{id}/contributions`                        | Đã đăng nhập  |   Không    | Danh sách lịch sử nạp/rút tiền của mục tiêu (phân trang & lọc)   |
| **`PATCH`**  | `/goals/{id}/contributions/{contributionId}`       | Đã đăng nhập  |   Không    | Chỉnh sửa số tiền, ngày hoặc ghi chú đợt đóng góp               |
| **`DELETE`** | `/goals/{id}/contributions/{contributionId}`       | Đã đăng nhập  |   Không    | Xóa mềm một đợt đóng góp (kiểm tra chống âm số dư)              |
| **`GET`**    | `/admin/goals/all`                                 | `GOAL_READ`   |   Không    | Admin tra cứu mục tiêu toàn hệ thống (phân trang & lọc)         |
| **`GET`**    | `/admin/goals/{user_id}`                           | `GOAL_READ`   |   Không    | Admin xem danh sách mục tiêu của một người dùng cụ thể          |
| **`GET`**    | `/admin/goals/detail/{id}`                         | `GOAL_READ`   |   Không    | Admin xem chi tiết mục tiêu bất kỳ                              |
| **`POST`**   | `/admin/goals/{userId}`                            | `GOAL_UPDATE` |   **Có**   | Admin tạo mục tiêu hộ cho một người dùng cụ thể                 |
| **`PATCH`**  | `/admin/goals/{id}`                                | `GOAL_UPDATE` |   Không    | Admin chỉnh sửa thông tin mục tiêu bất kỳ                       |
| **`DELETE`** | `/admin/goals/{id}`                                | `GOAL_DELETE` |   Không    | Admin xóa mềm mục tiêu bất kỳ                                   |
| **`POST`**   | `/admin/goals/{id}/contributions`                  | `GOAL_UPDATE` |   **Có**   | Admin nạp tiền vào mục tiêu bất kỳ                              |
| **`GET`**    | `/admin/goals/{id}/contributions`                  | `GOAL_READ`   |   Không    | Admin xem danh sách đóng góp của mục tiêu bất kỳ                |
| **`PATCH`**  | `/admin/goals/{id}/contributions/{contributionId}` | `GOAL_UPDATE` |   Không    | Admin chỉnh sửa đợt đóng góp bất kỳ                             |
| **`DELETE`** | `/admin/goals/{id}/contributions/{contributionId}` | `GOAL_DELETE` |   Không    | Admin xóa đợt đóng góp bất kỳ                                   |

---

## 3. Đặc tả Chi tiết Endpoints Người dùng <a id="3-dac-ta-chi-tiet-endpoints-nguoi-dung"></a>

### 3.1 Danh sách mục tiêu (`GET /goals`) <a id="31-danh-sach-muc-tieu"></a>

- **Method / Path:** `GET /goals`
- **Query Parameters (`SavingGoalFilterReq` & `Pageable`):**
    - `name` (String, tùy chọn): Tìm kiếm tương đối theo tên mục tiêu.
    - `status` (String, tùy chọn): `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.
    - `target_date_from` (Date `YYYY-MM-DD`, tùy chọn): Ngày hạn mục tiêu từ ngày.
    - `target_date_to` (Date `YYYY-MM-DD`, tùy chọn): Ngày hạn mục tiêu đến ngày.
    - `page` (Integer, mặc định: 0): Số thứ tự trang.
    - `size` (Integer, mặc định: 20): Số lượng bản ghi mỗi trang.
    - `sort` (String, mặc định: `createdAt,desc`): Sắp xếp.
- **Response Body (`SavingGoalListRes`):**

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "e4222f77-ec07-42f8-9a25-3cb49a464539",
        "name": "Mua MacBook Pro M4",
        "target_amount": 50000000,
        "saved_amount": 15000000,
        "target_date": "2026-12-31",
        "icon_code": "laptop",
        "note": "Nuôi heo đất trong tủ",
        "status": "IN_PROGRESS",
        "created_at": "2026-10-25T10:00:00Z",
        "updated_at": "2026-10-27T08:30:00Z"
      }
    ],
    "meta": {
      "page": 0,
      "page_size": 20,
      "total_items": 1,
      "total_pages": 1
    }
  },
  "msg": "Thành công"
}
```

---

### 3.2 Chi tiết mục tiêu (`GET /goals/{id}`) <a id="32-chi-tiet-muc-tieu"></a>

- **Method / Path:** `GET /goals/{id}`
- **Response Body (`SavingGoalRes`):**

```json
{
  "success": true,
  "data": {
    "id": "e4222f77-ec07-42f8-9a25-3cb49a464539",
    "name": "Mua MacBook Pro M4",
    "target_amount": 50000000,
    "saved_amount": 15000000,
    "target_date": "2026-12-31",
    "icon_code": "laptop",
    "note": "Nuôi heo đất trong tủ",
    "status": "IN_PROGRESS",
    "created_at": "2026-10-25T10:00:00Z",
    "updated_at": "2026-10-27T08:30:00Z"
  },
  "msg": "Thành công"
}
```

---

### 3.3 Tạo mục tiêu mới (`POST /goals`) <a id="33-tao-muc-tieu"></a>

- **Method / Path:** `POST /goals`
- **Header:** `Idempotency-Key: <UUID>` (Khuyến nghị để chống gửi trùng)
- **Request Body (`SavingGoalCreateReq`):**

```json
{
  "name": "Mua MacBook Pro M4",
  "target_amount": 50000000,
  "target_date": "2026-12-31",
  "icon_code": "laptop",
  "initial_amount": 5000000,
  "note": "Nuôi heo đất trong tủ"
}
```

- **Hành vi:**
    - Khởi tạo mục tiêu với trạng thái mặc định `IN_PROGRESS`.
    - Nếu `initial_amount > 0`, tự động tạo một bản ghi đóng góp đầu tiên ("Khoản tích lũy ban đầu").
- **Response Status:** `201 Created`

---

### 3.4 Cập nhật mục tiêu (`PATCH /goals/{id}`) <a id="34-cap-nhat-muc-tieu"></a>

- **Method / Path:** `PATCH /goals/{id}`
- **Request Body (`SavingGoalUpdateRequest`):**

```json
{
  "name": "Mua MacBook Pro M4 Pro",
  "target_amount": 55000000,
  "target_date": "2027-01-15",
  "icon_code": "laptop",
  "note": "Chuyển sang gửi sổ tiết kiệm Agribank",
  "status": "IN_PROGRESS"
}
```

- **Quy tắc:** Chỉ được phép đổi thủ công giữa `IN_PROGRESS` và `CANCELLED`. Trạng thái `COMPLETED` do hệ thống/Trigger quản lý dựa trên số tiền tích lũy.

---

### 3.5 Xóa mục tiêu tiết kiệm (`DELETE /goals/{id}`) <a id="35-xoa-muc-tieu"></a>

- **Method / Path:** `DELETE /goals/{id}`
- **Hành vi:** Xóa mềm (`is_deleted = true`) mục tiêu và toàn bộ danh sách các khoản đóng góp thuộc mục tiêu đó.
- **Response Status:** `200 OK`

---

### 3.6 Nộp tiền tích lũy (`POST /goals/{id}/contributions`) <a id="36-nap-tien-muc-tieu"></a>

- **Method / Path:** `POST /goals/{id}/contributions`
- **Header:** `Idempotency-Key: <UUID>`
- **Request Body (`ContributionGoalCreateReq`):**

```json
{
  "amount": 2000000,
  "contributed_date": "2026-10-27",
  "note": "Tiền thưởng dự án"
}
```

- **Hành vi:**
    - Chỉ cho phép nạp tiền khi mục tiêu đang ở trạng thái `IN_PROGRESS` (chặn khi đã `COMPLETED` hoặc `CANCELLED`).
    - Database Trigger tự động cộng dồn `saved_amount` và đổi trạng thái sang `COMPLETED` khi đủ tiền.
    - Bắn sự kiện `GoalCompletedEvent` để thông báo chúc mừng nếu mục tiêu vừa chạm đích.
- **Response Status:** `201 Created`
- **Response Body (`ContributionGoalRes`):**

```json
{
  "success": true,
  "data": {
    "id": "c138b1f5-1c39-4467-b5b6-76c27c2957e8",
    "goal_id": "e4222f77-ec07-42f8-9a25-3cb49a464539",
    "amount": 2000000,
    "contributed_date": "2026-10-27",
    "note": "Tiền thưởng dự án",
    "created_at": "2026-10-27T10:15:00Z",
    "updated_at": "2026-10-27T10:15:00Z"
  },
  "msg": "Thành công"
}
```

---

### 3.7 Danh sách lịch sử nạp/rút tiền (`GET /goals/{id}/contributions`) <a id="37-danh-sach-dong-gop"></a>

- **Method / Path:** `GET /goals/{id}/contributions`
- **Query Parameters (`ContributionGoalFilterReq` & `Pageable`):**
    - `is_withdrawal` (Boolean, tùy chọn): `true` (chỉ khoản rút), `false` (chỉ khoản nạp), để trống (tất cả).
    - `min_amount` (Long, tùy chọn): Số tiền giao dịch tối thiểu.
    - `max_amount` (Long, tùy chọn): Số tiền giao dịch tối đa.
    - `contributed_date_from` (Date `YYYY-MM-DD`, tùy chọn): Ngày giao dịch từ ngày.
    - `contributed_date_to` (Date `YYYY-MM-DD`, tùy chọn): Ngày giao dịch đến ngày.
    - `page` (Integer, mặc định: 0) & `size` (Integer, mặc định: 20).
- **Response Body (`ContributionGoalListRes`):**

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": "c138b1f5-1c39-4467-b5b6-76c27c2957e8",
        "goal_id": "e4222f77-ec07-42f8-9a25-3cb49a464539",
        "amount": 2000000,
        "contributed_date": "2026-10-27",
        "note": "Tiền thưởng dự án",
        "created_at": "2026-10-27T10:15:00Z",
        "updated_at": "2026-10-27T10:15:00Z"
      }
    ],
    "meta": {
      "page": 0,
      "page_size": 20,
      "total_items": 1,
      "total_pages": 1
    }
  },
  "msg": "Thành công"
}
```

---

### 3.8 Sửa khoản đóng góp (`PATCH /goals/{id}/contributions/{contributionId}`) <a id="38-sua-khoan-dong-gop"></a>

- **Method / Path:** `PATCH /goals/{id}/contributions/{contributionId}`
- **Request Body (`ContributionGoalUpdateReq`):**

```json
{
  "amount": 2500000,
  "contributed_date": "2026-10-27",
  "note": "Cập nhật lại tiền thưởng dự án"
}
```

- **Hành vi:**
    - Chặn cập nhật nếu số tiền sau sửa làm tổng số tiền tích lũy của mục tiêu bị âm.
    - Tự động duy trì dấu âm nếu đây là khoản rút tiền.
- **Response Status:** `200 OK`

---

### 3.9 Xóa đợt đóng góp (`DELETE /goals/{id}/contributions/{contributionId}`) <a id="39-xoa-khoan-dong-gop"></a>

- **Method / Path:** `DELETE /goals/{id}/contributions/{contributionId}`
- **Hành vi:**
    - Xóa mềm (`is_deleted = true`).
    - Kiểm tra bảo vệ: Chặn không cho xóa khoản nạp nếu việc xóa này khiến số tiền tích lũy còn lại bị âm (do trước đó đã có các lần rút tiền).
- **Response Status:** `200 OK`

---

## 4. Đặc tả Chi tiết Endpoints Quản trị viên (Admin) <a id="4-dac-ta-chi-tiet-endpoints-admin"></a>

Toàn bộ API Quản trị viên yêu cầu tài khoản có các quyền hạn tương ứng:
- Quyền đọc: `GOAL_READ`
- Quyền ghi/sửa: `GOAL_UPDATE`
- Quyền xóa: `GOAL_DELETE`

### 4.1 Quản lý mục tiêu (`/admin/goals`) <a id="41-quan-ly-muc-tieu-admin"></a>

- **`GET /admin/goals/all`** (`GOAL_READ`): Danh sách mục tiêu phân trang của toàn bộ người dùng trong hệ thống.
- **`GET /admin/goals/{user_id}`** (`GOAL_READ`): Danh sách mục tiêu phân trang của một người dùng cụ thể.
- **`GET /admin/goals/detail/{id}`** (`GOAL_READ`): Xem chi tiết bất kỳ mục tiêu nào trong hệ thống.
- **`POST /admin/goals/{userId}`** (`GOAL_UPDATE`, Idempotent): Admin tạo hộ mục tiêu tiết kiệm cho người dùng đích.
- **`PATCH /admin/goals/{id}`** (`GOAL_UPDATE`): Admin chỉnh sửa thông tin bất kỳ mục tiêu nào.
- **`DELETE /admin/goals/{id}`** (`GOAL_DELETE`): Admin xóa mềm mục tiêu bất kỳ.

### 4.2 Quản lý đóng góp (`/admin/goals/{id}/contributions`) <a id="42-quan-ly-dong-gop-admin"></a>

- **`POST /admin/goals/{id}/contributions`** (`GOAL_UPDATE`, Idempotent): Admin nạp tiền vào mục tiêu cho user.
- **`GET /admin/goals/{id}/contributions`** (`GOAL_READ`): Admin tra cứu lịch sử đóng góp của mục tiêu bất kỳ.
- **`PATCH /admin/goals/{id}/contributions/{contributionId}`** (`GOAL_UPDATE`): Admin chỉnh sửa đợt đóng góp bất kỳ.
- **`DELETE /admin/goals/{id}/contributions/{contributionId}`** (`GOAL_DELETE`): Admin xóa đợt đóng góp bất kỳ.

---

## 5. Bảng Ma trận Mã lỗi (Error Code Matrix) <a id="5-bang-ma-tran-ma-loi"></a>

| Mã lỗi                               | HTTP Status       | Mô tả nghiệp vụ                                                                  |
|:-------------------------------------|:------------------|:---------------------------------------------------------------------------------|
| `NOT_FOUND`                          | `404 Not Found`   | Không tìm thấy mục tiêu tiết kiệm thuộc quyền sở hữu                             |
| `GOAL_CONTRIBUTION_NOT_FOUND`        | `404 Not Found`   | Không tìm thấy lượt đóng góp tương ứng                                          |
| `GOAL_ALREADY_COMPLETED`             | `409 Conflict`    | Mục tiêu đã đạt mốc hoàn thành, không thể thao tác thêm                         |
| `GOAL_CANCELLED`                     | `409 Conflict`    | Mục tiêu đã bị hủy                                                              |
| `GOAL_WITHDRAW_EXCEEDS_SAVED`        | `400 Bad Request` | Số tiền rút vượt quá số tiền tích lũy hiện có trong mục tiêu                    |
| `GOAL_CONTRIBUTION_NEGATIVE_BALANCE` | `400 Bad Request` | Không thể cập nhật hoặc xóa khoản đóng góp vì số tiền tích lũy còn lại sẽ bị âm |
| `VALIDATION_ERROR`                   | `400 Bad Request` | Dữ liệu gửi lên không thỏa mãn Bean Validation (tên trống, tiền <= 0...)        |
| `FORBIDDEN`                          | `403 Forbidden`   | Không đủ quyền Admin để thực thi thao tác quản trị                              |
