# ĐẶC TẢ API — PHÂN HỆ THANH TOÁN (PAYMENT) & NÂNG CẤP PREMIUM

> **File này trả lời:** Cách client nâng cấp tài khoản FREE → PREMIUM qua payOS, tra cứu trạng thái đơn, xem lịch sử thanh toán, huỷ đơn thanh toán và các endpoint payOS gọi về server.  
> Endpoint tạo đơn nằm ở module `user` (`POST /users/me/premium-upgrades`) vì nâng cấp là hành động của user; module `pay` lo ghi nhận đơn, webhook và lịch sử.

---

## 📑 MỤC LỤC

- [1. Luồng nâng cấp phía client](#1-luong-client)
- [2. Danh mục Endpoint](#2-danh-muc-endpoint)
- [3. Chi tiết API](#3-chi-tiet-api)
- [4. Quy tắc nghiệp vụ](#4-quy-tac)
- [5. Bảng mã lỗi](#5-bang-ma-loi)
- [6. Cấu hình](#6-cau-hinh)

---

## 1. Luồng nâng cấp phía client <a id="1-luong-client"></a>

1. Client gọi `POST /users/me/premium-upgrades` với `plan_code` → nhận `checkout_url`, `order_code`, `expires_at`.
2. Client mở `checkout_url` (WebView hoặc trình duyệt ngoài, do FE quyết định, server không phụ thuộc).
3. User thanh toán trên trang payOS. payOS gọi webhook về server; server ghi `PAID` và nâng cấp user.
4. Client **polling** `GET /payments/{order_code}` mỗi 2–3 giây cho tới khi `status` khác `PENDING`.
5. Khi `status = PAID`: client gọi refresh token (hoặc `GET /users/me`) để lấy `plan = PREMIUM` và `plan_expires_at`.
6. User có thể chủ động bấm huỷ qua `DELETE /payments/{order_code}` nếu muốn huỷ đơn chờ hiện tại.

> `return_url` / `cancel_url` chỉ là trang đích sau thanh toán, **không** đổi dữ liệu. Có thể trỏ về deep link của app thay vì endpoint của server.

---

## 2. Danh mục Endpoint <a id="2-danh-muc-endpoint"></a>

| Method | Path | Quyền | Mô tả |
| :--- | :--- | :--- | :--- |
| **`POST`** | `/users/me/premium-upgrades` | Đã đăng nhập | Tạo đơn nâng cấp premium (`@Idempotent`) |
| **`GET`** | `/payments` | Đã đăng nhập | Lịch sử thanh toán của chính mình |
| **`GET`** | `/payments/{order_code}` | Đã đăng nhập | Trạng thái một đơn của chính mình (dùng để polling) |
| **`DELETE`** | `/payments/{order_code}` | Đã đăng nhập | Huỷ đơn thanh toán của chính mình |
| **`GET`** | `/payments/admin` | `PAYMENT_READ` hoặc `ROLE_ADMIN` | Lịch sử thanh toán toàn hệ thống |
| **`GET`** | `/payments/admin/{order_code}` | `PAYMENT_READ` hoặc `ROLE_ADMIN` | Chi tiết một đơn bất kỳ |
| **`POST`** | `/public/payments/payos/webhook` | Public (verify chữ ký payOS) | payOS gọi khi có thanh toán |
| **`GET`** | `/public/payments/payos/return` | Public | Trang HTML "đóng cửa sổ" sau khi thanh toán |
| **`GET`** | `/public/payments/payos/cancel` | Public | Trang HTML "đóng cửa sổ" khi huỷ thanh toán |

---

## 3. Chi tiết API <a id="3-chi-tiet-api"></a>

### 3.1 Tạo đơn nâng cấp premium

- **Method / Path:** `POST /users/me/premium-upgrades`
- **Header tuỳ chọn:** `Idempotency-Key` (gửi lại cùng key trả lại đúng response cũ).
- **Request:**
```json
{ "plan_code": "PREMIUM_6M" }
```
`plan_code` nhận `PREMIUM_1M`, `PREMIUM_6M`, `PREMIUM_12M`. **Client không gửi số tiền**, giá do server tính.

- **Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "order_code": 100002,
    "reference_id": "PREMIUM_6M",
    "amount": 249000,
    "status": "PENDING",
    "checkout_url": "https://pay.payos.vn/web/xxxx",
    "expires_at": "2026-10-20T10:15:00Z"
  },
  "msg": ""
}
```
- **Đang có đơn chờ chưa hết hạn:** Server **luôn huỷ link cũ** (chuyển trạng thái đơn cũ thành `CANCELLED`), rồi tạo đơn mới và link mới với gói vừa chọn. (Trường hợp trước khi huỷ mà user đã trả tiền bên payOS thì server ghi nhận `PAID` và trả về đơn đã trả, không tạo mới).
- **Lỗi:** `PREMIUM_ALREADY_ACTIVE` (409), `REQUEST_IN_PROGRESS` (409, đơn đang được webhook/job xử lý, FE thử lại sau 1-2s), `VALIDATION_ERROR` (400, `plan_code` sai hoặc thiếu), `PAYMENT_PROVIDER_ERROR` (502), `PAYMENT_NOT_CONFIGURED` (503).

### 3.2 Trạng thái một đơn

- **Method / Path:** `GET /payments/{order_code}`
- **Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "order_code": 100001,
    "type": "PREMIUM_UPGRADE",
    "reference_id": "PREMIUM_6M",
    "amount": 249000,
    "amount_paid": 249000,
    "excess_amount": 0,
    "status": "PAID",
    "paid_at": "2026-10-20T10:03:11Z",
    "expires_at": "2026-10-20T10:15:00Z",
    "created_at": "2026-10-20T10:00:00Z"
  },
  "msg": ""
}
```
`status` nhận: `PENDING`, `PAID`, `CANCELLED`, `EXPIRED`. Đơn của người khác trả `404 NOT_FOUND`.  
`amount_paid`: Số tiền thực tế payOS đã thu (chỉ có khi đơn `PAID`).  
`excess_amount`: Số tiền thanh toán thừa (`amount_paid - amount` nếu > 0, ngược lại `0`).

### 3.3 Huỷ một đơn thanh toán

- **Method / Path:** `DELETE /payments/{order_code}`
- **Response (`200 OK`):**
```json
{
  "success": true,
  "data": {
    "order_code": 100001,
    "type": "PREMIUM_UPGRADE",
    "reference_id": "PREMIUM_6M",
    "amount": 249000,
    "amount_paid": null,
    "excess_amount": 0,
    "status": "CANCELLED",
    "paid_at": null,
    "expires_at": "2026-10-20T10:15:00Z",
    "created_at": "2026-10-20T10:00:00Z"
  },
  "msg": ""
}
```
Endpoint có tính idempotent: nếu đơn đã ở trạng thái kết thúc (`CANCELLED`, `EXPIRED`, `PAID`), server trả lại trạng thái hiện tại mà không ném lỗi.

### 3.4 Lịch sử thanh toán của tôi

- **Method / Path:** `GET /payments?status=&type=&page=1&page_size=20`
- **Response:** `data.items` (cùng cấu trúc 3.2) + `data.meta` (`page`, `page_size`, `total_items`, `total_pages`). Mới nhất trước.

### 3.5 Quản trị: danh sách & chi tiết

- `GET /payments/admin?user_id=&status=&type=&from_date=&to_date=&page=1&page_size=20` — `from_date`/`to_date` dạng `yyyy-MM-dd` (giờ Việt Nam), `to_date` tính cả ngày đó.
- `GET /payments/admin/{order_code}` — chi tiết đơn bất kỳ.
- Mỗi phần tử có thêm `user_id`, `payos_link_id`, `updated_at`, `amount_paid`, `excess_amount`. Chỉ đọc, không có thao tác ghi.

### 3.6 Webhook payOS

- `POST /public/payments/payos/webhook` — body do payOS gửi (camelCase, ví dụ `orderCode`), server verify chữ ký bằng `checksum-key`.
- Chữ ký sai hoặc body hỏng → `400 PAYMENT_WEBHOOK_INVALID`.
- Không tìm thấy đơn / link không khớp / đơn đã xử lý → log và trả `200`, không đổi dữ liệu.
- Nâng cấp lỗi → rollback và trả 5xx để payOS gửi lại.

---

## 4. Quy tắc nghiệp vụ <a id="4-quy-tac"></a>

- **Tạo link mới:** Mỗi lần user yêu cầu nâng cấp, server sẽ huỷ link cũ và tạo link mới. Mỗi link chỉ sống độc nhất, không bao giờ có 2 link cùng mở cho một user.
- **Thanh toán thừa tiền (trả dư):** Nếu số tiền thu `>=` số tiền yêu cầu của đơn, đơn vẫn được kích hoạt `PAID` và giao hàng. Số tiền thừa không hoàn tự động, được lưu vào `amount_paid` và tính `excess_amount` để tra cứu khi user khiếu nại.
- **Thanh toán thiếu tiền (trả thiếu):** Không giao hàng, giữ đơn `PENDING` và ghi log đối soát (Giai đoạn 2 sẽ xử lý luồng bù tiền trong thời hạn).
- **Môi trường & khoá payOS:** Mỗi môi trường (dev, test, prod) bắt buộc sử dụng một bộ khoá payOS riêng biệt để tránh xung đột mã đơn `order_code`.
- Đang premium còn hạn thì **không mua thêm** (`PREMIUM_ALREADY_ACTIVE`). Hết hạn mới được mua. Hạn mới = lúc nâng cấp + số tháng (1 / 6 / 12).
- Job `PremiumExpiryJob` (00:05 giờ Việt Nam hằng ngày) hạ user quá hạn về `FREE`. Access token cũ vẫn mang `plan = PREMIUM` đến khi hết hạn token; lần refresh kế tiếp nhận plan mới.
- Tài khoản premium không có `plan_expires_at` (VD admin seed) được coi là premium vô thời hạn: không mua được, không bị hạ.
- Không hoàn tiền, không huỷ gói. Đã trả thì dùng tới hết hạn.

---

## 5. Bảng mã lỗi <a id="5-bang-ma-loi"></a>

| Mã lỗi kỹ thuật | HTTP Status | Ý nghĩa |
| :--- | :---: | :--- |
| `PREMIUM_ALREADY_ACTIVE` | 409 | Gói premium còn hạn, hết hạn mới mua thêm |
| `VALIDATION_ERROR` | 400 | `plan_code` thiếu hoặc không hợp lệ |
| `PAYMENT_NOT_FOUND` | 404 | Không có đơn, hoặc đơn thuộc người khác |
| `PAYMENT_PROVIDER_ERROR` | 502 | payOS lỗi hoặc không phản hồi |
| `PAYMENT_NOT_CONFIGURED` | 503 | Chưa bật hoặc chưa cấu hình khoá payOS |
| `PAYMENT_WEBHOOK_INVALID` | 400 | Webhook sai chữ ký hoặc sai định dạng |
| `REQUEST_IN_PROGRESS` | 409 | Yêu cầu trước đó đang được xử lý hoặc đang tranh chấp khoá, vui lòng thử lại sau |

---

## 6. Cấu hình <a id="6-cau-hinh"></a>

Biến môi trường (`.env`, mẫu ở `.env.example`):

| Biến | Bắt buộc | Ý nghĩa |
| :--- | :---: | :--- |
| `PAYOS_ENABLED`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | Có | Khoá payOS |
| `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` | **Có** | URL payOS chuyển hướng user về; thiếu thì app không khởi động |
| `PAYOS_EXPIRED_MINUTES` | Không (15) | Thời hạn thanh toán của một đơn (tối thiểu 1 phút) |
| `PREMIUM_PRICE_1M`, `PREMIUM_PRICE_6M`, `PREMIUM_PRICE_12M` | **Có** | Giá gói (VND, số nguyên >= 2000); thiếu thì app không khởi động |
