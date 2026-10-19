# ĐẶC TẢ RESTFUL API ENDPOINTS — VÍ TÀI CHÍNH & ĐỐI CHIẾU (WALLET)

> **File này trả lời:** Danh sách và đặc tả chi tiết các RESTful API endpoints phân hệ Ví Tài Chính (`/wallets`), cơ chế tự động cân bằng số dư ban đầu, xóa mềm cascade qua Database Trigger, cơ chế đối chiếu số dư (reconciliation), và kịch bản kiểm thử API.  
> Code chính → [`WalletController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/wallet/controller/WalletController.java) · [`ReconcilHistoryController.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/wallet/controller/ReconcilHistoryController.java) · [`WalletBehavierImpl.java`](file:///D:/projects/java/server/src/main/java/com/datn/financeapp/module/wallet/service/impl/WalletBehavierImpl.java) · Luồng nghiệp vụ → [docs/work_flow/wallet.md](../work_flow/wallet.md)

---

## 📑 MỤC LỤC

- [1. Quy ước chung](#1-quy-uoc-chung)
- [2. Danh mục Endpoint Tổng hợp](#2-danh-muc-endpoint)
- [3. Đặc tả Chi tiết Endpoints Người dùng](#3-dac-ta-chi-tiet-endpoints-nguoi-dung)
  - [3.1 Danh sách ví cá nhân (`GET /wallets`)](#31-danh-sach-vi)
  - [3.2 Chi tiết một ví (`GET /wallets/{id}`)](#32-chi-tiet-vi)
  - [3.3 Tạo ví mới (`POST /wallets`)](#33-tao-vi)
  - [3.4 Cập nhật thông tin ví (`PATCH /wallets/{id}`)](#34-cap-nhat-vi)
  - [3.5 Xóa mềm ví (`DELETE /wallets/{id}`)](#35-xoa-vi)
  - [3.6 Lịch sử đối chiếu số dư ví (`GET /wallets/{id}/reconciliations`)](#36-doi-chieu-vi)
- [4. Đặc tả Chi tiết Endpoints Quản trị viên (`/wallets/admin`)](#4-dac-ta-chi-tiet-endpoints-admin)
- [5. Cấu trúc Thực thể JPA và DTO](#5-cau-truc-thuc-the-va-dto)
- [6. Biểu đồ Lớp Phân tầng (Spring Boot Class Diagram)](#6-bieu-do-lop-phan-tang)
- [7. Biểu đồ Tuần tự (Sequence Diagrams)](#7-bieu-do-tuan-tu)
- [8. Bảng Ma trận Mã lỗi (Error Code Matrix)](#8-bang-ma-tran-ma-loi)
- [9. Kịch bản Kiểm thử REST API (Spring Boot Test Cases)](#9-kich-ban-kiem-thu-rest-api)

---

## 1. Quy ước chung <a id="1-quy-uoc-chung"></a>

- **Base URL:** `/wallets`
- **Định dạng:** `Content-Type: application/json`, Jackson cấu hình `snake_case`.
- **Đóng gói chuẩn:** Mọi response bọc trong `ApiResponse<T>`.
- **Xác thực phiên:** `Authorization: Bearer <accessToken>`.
- **Phân quyền RBAC:**
  - Luồng User: Xem và quản lý ví thuộc quyền sở hữu của tài khoản hiện tại.
  - Luồng Admin (`/wallets/admin/**`):
    - Đọc: `wallet:read` (`PermissionName.WALLET_READ`).
    - Ghi / Cập nhật: `wallet:update` (`PermissionName.WALLET_UPDATE`).
    - Xóa: `wallet:delete` (`PermissionName.WALLET_DELETE`).
- **Quy tắc số dư & Trigger CSDL:**
  - `Wallet.balance` có `updatable = false` trong Java Entity. Java **không** tự tính toán sửa đổi `balance`.
  - Trigger `trg_wallets_initial_transaction` ([`V7`](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V7__trigger_wallet_balance.sql)): Khi ví tạo mới có `balance > 0`, trigger đưa balance về 0 rồi tự động tạo giao dịch `INCOME` "Số dư ban đầu" (không gắn danh mục), sau đó trigger tính số dư cộng lại vào ví.
  - Trigger `trg_wallets_soft_delete_cascade` ([`V8`](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V8__trigger_wallet_soft_delete.sql)): Khi ví bị xóa mềm (`is_deleted = true`), tự động xóa mềm mọi giao dịch của ví, tắt giao dịch định kỳ (`is_enabled = false`), chuyển lịch `PENDING` thành `SKIPPED`.
  - Trigger `trg_wallets_only_one_default` ([`V6`](file:///D:/projects/java/server/src/main/resources/db/migration_v2/V6__trigger_wallet_default.sql)): Mỗi người dùng bắt buộc có duy nhất 1 ví mặc định (`is_default = true`) chưa xóa.

---

## 2. Danh mục Endpoint Tổng hợp <a id="2-danh-muc-endpoint"></a>

| Phương thức | Endpoint | Quyền hạn | Idempotent | Mô tả |
| :--- | :--- | :--- | :---: | :--- |
| **`GET`** | `/wallets` | Đã đăng nhập | Không | Danh sách ví chưa xóa của người dùng, phân trang và kèm icon SVG |
| **`GET`** | `/wallets/{id}` | Đã đăng nhập | Không | Xem thông tin chi tiết một ví của người dùng |
| **`POST`** | `/wallets` | Đã đăng nhập | **Có** | Tạo ví mới (ngăn tạo thủ công ví `SAVINGS`) |
| **`PATCH`** | `/wallets/{id}` | Đã đăng nhập | Không | Cập nhật tên, màu sắc, biểu tượng của ví |
| **`DELETE`** | `/wallets/{id}` | Đã đăng nhập | Không | Xóa mềm ví (ngăn xóa ví mặc định) |
| **`GET`** | `/wallets/{id}/reconciliations` | Đã đăng nhập | Không | Xem lịch sử các lần đối chiếu số dư của ví |
| **`GET`** | `/wallets/admin/{user_id}` | `wallet:read` | Không | Admin xem danh sách ví của một người dùng |
| **`GET`** | `/wallets/admin/all` | `wallet:read` | Không | Admin xem toàn bộ ví hệ thống (kể cả đã xóa) |
| **`GET`** | `/wallets/admin/detail/{id}` | `wallet:read` | Không | Admin xem chi tiết bất kỳ ví nào |
| **`POST`** | `/wallets/admin/{user_id}` | `wallet:update` | **Có** | Admin tạo ví hộ người dùng |
| **`PATCH`** | `/wallets/admin/{id}` | `wallet:update` | Không | Admin cập nhật ví của người dùng |
| **`DELETE`** | `/wallets/admin/{id}` | `wallet:delete` | Không | Admin xóa mềm ví |
| **`GET`** | `/wallets/admin/reconciliations`| `wallet:read` | Không | Admin xem toàn bộ lịch sử đối chiếu hệ thống |

---

## 3. Đặc tả Chi tiết Endpoints Người dùng <a id="3-dac-ta-chi-tiet-endpoints-nguoi-dung"></a>

### 3.1 Danh sách ví cá nhân (`GET /wallets`) <a id="31-danh-sach-vi"></a>

- **Method / Path:** `GET /wallets`
- **Query Parameters:** `page`, `size`, `name`, `type`, `min_balance`, `max_balance`.
- **Response Status:** `200 OK`
- **Response Body (`ApiResponse<WalletPageRes>`):**

```json
{
  "success": true,
  "code": 200,
  "message": "Thành công",
  "data": {
    "wallets": [
      {
        "id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
        "name": "Tiền mặt",
        "type": "CASH",
        "balance": 2500000,
        "is_default": true,
        "icon": "default_wallet",
        "color": "#3d6b7d",
        "created_at": "2026-09-01T08:00:00Z"
      }
    ],
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

### 3.2 Tạo ví mới (`POST /wallets`) <a id="33-tao-vi"></a>

- **Method / Path:** `POST /wallets`
- **Headers:** `Idempotency-Key: <UUID>`
- **Request Body (`WalletCreateReq`):**

```json
{
  "name": "Tài khoản Techcombank",
  "type": "BANK",
  "balance": 5000000,
  "icon_code": "ngan_hang",
  "color": "#3f6b8a"
}
```

- **Ràng buộc:**
  - Không cho phép tạo ví loại `SAVINGS` (Ví tiết kiệm chỉ được tạo tự động khi tạo Mục tiêu tiết kiệm). Nếu gửi `type = SAVINGS` ➔ ném `WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED` (HTTP 400).
  - Tên ví không được trùng với ví khác đang hoạt động của cùng người dùng (`WALLET_NAME_EXISTS`, HTTP 409).
- **Response Status:** `201 Created`

---

### 3.3 Cập nhật ví (`PATCH /wallets/{id}`) <a id="34-cap-nhat-vi"></a>

- **Method / Path:** `PATCH /wallets/{id}`
- **Request Body (`WalletupdateReq`):**
  - Cho phép sửa: `name`, `color`, `icon_code`.
  - **Không** cho phép sửa trực tiếp `balance` hay `type` (muốn đổi số dư phải tạo giao dịch thu/chi).
- **Response Status:** `200 OK`

---

### 3.4 Xóa mềm ví (`DELETE /wallets/{id}`) <a id="35-xoa-vi"></a>

- **Method / Path:** `DELETE /wallets/{id}`
- **Ràng buộc:** Không được xóa ví đang là ví mặc định (`is_default = true`) ➔ ném `WALLET_DEFAULT_NOT_DELETABLE` (HTTP 409). Phải đặt ví khác làm mặc định trước.
- **Hệ quả CSDL:** Trigger xóa mềm toàn bộ giao dịch thuộc ví và hủy các lịch định kỳ.
- **Response Status:** `200 OK`

---

## 4. Bảng Ma trận Mã lỗi (Error Code Matrix) <a id="8-bang-ma-tran-ma-loi"></a>

| Mã lỗi Enum | HTTP Status | Nguyên nhân |
| :--- | :--- | :--- |
| `WALLET_NAME_EXISTS` | `409 Conflict` | Trùng tên ví với ví khác chưa xóa của cùng người dùng |
| `WALLET_DEFAULT_NOT_DELETABLE` | `409 Conflict` | Cố ý xóa ví đang giữ cờ mặc định (`is_default = true`) |
| `WALLET_SAVINGS_MANUAL_CREATE_NOT_ALLOWED` | `400 Bad Request` | Tạo thủ công ví tiết kiệm qua `POST /wallets` |
| `WALLET_BALANCE_NOT_EDITABLE` | `400 Bad Request` | Cố gắng sửa số dư ví qua phương thức `PATCH` |
| `NOT_FOUND` | `404 Not Found` | Ví không tồn tại hoặc thuộc người dùng khác |
| `ICON_NOT_FOUND` | `404 Not Found` | Mã icon không tồn tại hoặc đã bị vô hiệu hóa |

---

## 5. Kịch bản Kiểm thử REST API (Spring Boot Test Cases) <a id="9-kich-ban-kiem-thu-rest-api"></a>

| Mã Test Case | Phân loại | Mục đích kiểm thử | Đầu vào HTTP Request | Tiền điều kiện CSDL | Kỳ vọng |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **TC_WAL_01** | Luồng chuẩn | Tạo ví mới có số dư ban đầu | `POST /wallets`<br>Body: name="VCB", balance=1.000.000 | User hợp lệ | `201 Created`, CSDL sinh giao dịch thu 1tr |
| **TC_WAL_02** | Ràng buộc | Chặn tạo thủ công ví SAVINGS | `POST /wallets`<br>Body: type=SAVINGS | Bất kỳ | `400 Bad Request` (`SAVINGS_MANUAL...`) |
| **TC_WAL_03** | Ràng buộc | Chặn xóa ví mặc định | `DELETE /wallets/{id_vi_mac_dinh}` | Ví có `is_default=true` | `409 Conflict` (`WALLET_DEFAULT_NOT_DELETABLE`) |
| **TC_WAL_04** | Trùng lặp | Chặn trùng tên ví trong cùng user | `POST /wallets`<br>Body: name="Tiền mặt" | User đã có ví "Tiền mặt" | `409 Conflict` (`WALLET_NAME_EXISTS`) |
| **TC_WAL_05** | Cascade | Xóa ví làm mềm giao dịch liên quan | `DELETE /wallets/{id}` | Ví có 5 giao dịch | `200 OK`, cả 5 giao dịch đổi `is_deleted=true` |
| **TC_WAL_06** | Cách ly | Không truy cập được ví người khác | `GET /wallets/{id}` của user khác | User B sở hữu | `404 Not Found` |
