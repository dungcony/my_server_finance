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
- [7. Chạy trên máy dev & xử lý sự cố](#7-su-co)
- [8. Dashboard webhook (admin)](#8-dashboard-webhook)

---

## 1. Luồng nâng cấp phía client <a id="1-luong-client"></a>

1. Client gọi `POST /users/me/premium-upgrades` với `plan_code` → nhận `checkout_url`, `order_code`, `expires_at`.
2. Client mở `checkout_url` (WebView hoặc trình duyệt ngoài, do FE quyết định, server không phụ thuộc).
3. User thanh toán trên trang payOS. payOS gọi **webhook** về server; server ghi `PAID` và nâng cấp user ngay, user không phải làm gì thêm. (Khi chạy trên máy dev, payOS không gọi được `localhost` nên webhook không tới, xem [mục 7](#7-su-co).)
4. Client **polling** `GET /payments/{order_code}` mỗi 2–3 giây cho tới khi `status` khác `PENDING`. Mỗi lần gọi, nếu đơn còn `PENDING`, server hỏi lại payOS và ghi `PAID` nếu payOS báo đã nhận đủ tiền, nên polling cũng là đường dự phòng khi webhook chưa tới.
5. Khi `status = PAID`: client gọi refresh token (hoặc `GET /users/me`) để lấy `plan = PREMIUM` và `plan_expires_at`.
6. User có thể chủ động bấm huỷ qua `DELETE /payments/{order_code}` nếu muốn huỷ đơn chờ hiện tại.

> `return_url` / `cancel_url` chỉ là trang đích sau thanh toán, **không** đổi dữ liệu và **không** nâng cấp user (việc nâng cấp do webhook và polling làm). Có thể trỏ về deep link của app thay vì endpoint của server. Nếu trỏ về endpoint của server thì URL phải gồm context-path (VD `/v1`).

> **Khuyến nghị cho FE:** mỗi lần user bấm "Nâng cấp" sinh một `Idempotency-Key` (UUID) mới, retry do mạng thì gửi lại đúng key đó; khoá nút trong lúc chờ phản hồi. Response của lần gọi sau cùng luôn là đơn/link đang có hiệu lực, link của response cũ đã bị huỷ.

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
| **`GET`** | `/payments/admin/webhook-logs` | `PAYMENT_READ` hoặc `ROLE_ADMIN` | Nhật ký các lần payOS gọi webhook (lọc theo kết quả, thời gian, `order_code`) |
| **`GET`** | `/payments/admin/webhook-logs/summary` | `PAYMENT_READ` hoặc `ROLE_ADMIN` | Thống kê webhook và cảnh báo thiếu webhook |
| **`POST`** | `/public/payments/payos/webhook` | Public (verify chữ ký payOS) | payOS gọi khi có thanh toán |
| **`GET`** | `/public/payments/payos/return` | Public | Trang HTML "đóng cửa sổ" sau khi thanh toán, không đổi dữ liệu |
| **`GET`** | `/public/payments/payos/cancel` | Public | Trang HTML "đóng cửa sổ" khi huỷ thanh toán, không đổi dữ liệu |

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
- **`502 PAYMENT_PROVIDER_ERROR`** xảy ra khi: payOS lỗi hoặc không phản hồi; payOS từ chối tạo link (VD mã đơn đã tồn tại); huỷ link cũ không có hiệu lực; hoặc đơn cũ đã nhận **một phần** tiền (server không đóng đơn đó và không tạo đơn mới, xem mục 4).

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
`status` nhận: `PENDING`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`, `EXPIRED`, `FORFEITED`. Đơn của người khác trả `404 NOT_FOUND`.  
`amount_remaining`: Số tiền còn thiếu người dùng cần nạp bù (khi `PARTIALLY_PAID`).  
`topup_deadline`: Hạn chót bù tiền khi đơn ở trạng thái `PARTIALLY_PAID`.  
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
Endpoint có tính idempotent: nếu đơn đã ở trạng thái kết thúc (`CANCELLED`, `EXPIRED`, `PAID`), server trả lại trạng thái hiện tại mà không ném lỗi. Đơn đang `PENDING`: server hỏi payOS, nếu **chưa nhận đồng nào** thì huỷ link rồi ghi `CANCELLED`; nếu payOS báo đã nhận đủ tiền (user vừa trả đúng lúc) thì ghi `PAID` và trả về `PAID` thay vì `CANCELLED`, nên **client luôn phải đọc `status` trong response**.
- Lỗi: `404 NOT_FOUND` (đơn không có hoặc của người khác), `502 PAYMENT_PROVIDER_ERROR` (payOS lỗi, hoặc đơn đã nhận một phần tiền nên không đóng được).

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
- Webhook không phải thanh toán thành công (`success=false` hoặc `code` khác `00`) → bỏ qua, trả `200`.
- Không tìm thấy đơn: `orderCode >= 100000` log `ERROR [PAYMENT-RECONCILE]` (có thể là link mồ côi, cần đối soát), nhỏ hơn thì chỉ `INFO` (VD request thử khi đăng ký webhook); luôn trả `200`.
- `paymentLinkId` không khớp link đang lưu của đơn → log `ERROR`, bỏ qua, trả `200`.
- Đơn đã `PAID` (payOS gửi lại) → bỏ qua, không nâng cấp lần hai.
- Đơn đã `CANCELLED`/`EXPIRED` mà vẫn nhận tiền → **không đổi đơn, không giao hàng**, log `ERROR [PAYMENT-RECONCILE]` (bất biến bị vỡ, cần xử lý tay).
- Số tiền thu nhỏ hơn số đơn → không giao hàng, giữ `PENDING`, log `ERROR [PAYMENT-RECONCILE]`.
- Đủ hoặc dư tiền → `PAID`, giao hàng.
- Nâng cấp lỗi do `referenceId` sai → rollback và trả 5xx để payOS gửi lại. User đã bị xoá → đơn vẫn `PAID`, bỏ qua bước nâng cấp, log `ERROR`, trả `200`.
- Endpoint nằm dưới context-path (VD `/v1/public/payments/payos/webhook`); URL đăng ký với payOS phải gồm đủ.

---

## 4. Quy tắc nghiệp vụ <a id="4-quy-tac"></a>

- **Tạo link mới:** Mỗi lần user yêu cầu nâng cấp, server sẽ huỷ link cũ và tạo link mới. Mỗi link chỉ sống độc nhất, không bao giờ có 2 link cùng mở cho một user.
- **Thanh toán thừa tiền (trả dư):** Nếu số tiền thu `>=` số tiền yêu cầu của đơn, đơn vẫn được kích hoạt `PAID` và giao hàng. Số tiền thừa không hoàn tự động, được lưu vào `amount_paid` và tính `excess_amount` để tra cứu khi user khiếu nại.
- **Thanh toán thiếu tiền (trả thiếu):** Khi phát hiện tiền nhận được nhỏ hơn số tiền yêu cầu (`0 < amount_paid < amount`), payOS tự động cập nhật số tiền thiếu trên mã QR cũ. Đơn hàng chuyển sang `PARTIALLY_PAID`, ghi nhận `amount_paid`, `amount_remaining` và đặt hạn chót bù tiền `topup_deadline`. Hệ thống gửi thông báo yêu cầu nạp bù. Khách chỉ cần quét lại QR cũ để chuyển nốt. Nếu nạp đủ, đơn chuyển `PAID` và kích hoạt dịch vụ. Quá hạn `topup_deadline`, job `PaymentTopupExpiryJob` huỷ link và chuyển trạng thái đơn sang `FORFEITED`, phát thông báo đóng đơn và giữ tiền đã nạp.
- **Chỉ ghi `CANCELLED`/`EXPIRED` khi link payOS chắc chắn đã chết:** server hỏi payOS, huỷ link, rồi đọc lại trạng thái; chưa chắc link đã chết thì giữ `PENDING`. Nhờ vậy đơn `CANCELLED`/`EXPIRED` không thể nhận tiền.
- **Dọn đơn quá hạn:** job `PaymentExpiryJob` chạy mỗi 5 phút, với đơn `PENDING` quá `expires_at` thì hỏi payOS: đã trả đủ thì ghi `PAID`, chưa có tiền thì huỷ link rồi ghi `EXPIRED`. Tạo đơn và hỏi trạng thái cũng kiểm tra lại hạn nên user không phải đợi job.
- **Thứ tự khoá:** luôn khoá đơn thanh toán trước, tài nguyên khác (user) sau. Tạo đơn khoá đơn chờ theo kiểu không chờ (`NOWAIT`): đơn đang bị webhook/job xử lý thì trả `409 REQUEST_IN_PROGRESS`, FE thử lại sau 1–2 giây với cùng `Idempotency-Key`.
- **Môi trường & khoá payOS:** Mỗi môi trường (dev, test, prod) bắt buộc sử dụng một bộ khoá payOS riêng biệt để tránh xung đột mã đơn `order_code`.
- Đang premium còn hạn thì **không mua thêm** (`PREMIUM_ALREADY_ACTIVE`). Hết hạn mới được mua. Hạn mới = lúc nâng cấp + số tháng (1 / 6 / 12).
- Job `PremiumExpiryJob` (00:05 giờ Việt Nam hằng ngày) hạ user quá hạn về `FREE`. Access token cũ vẫn mang `plan = PREMIUM` đến khi hết hạn token; lần refresh kế tiếp nhận plan mới.
- Tài khoản premium không có `plan_expires_at` (VD admin seed) được coi là premium vô thời hạn: không mua được, không bị hạ.
- Không hoàn tiền, không huỷ gói đã trả. Đã trả thì dùng tới hết hạn. (`DELETE /payments/{order_code}` chỉ huỷ **đơn chưa trả**.)
- Premium được nâng cấp trong cùng transaction với việc ghi `PAID` (`PaymentPaidEvent` xử lý đồng bộ). Nếu đơn cũ được trả muộn lúc user đã premium thì cộng dồn từ hạn hiện tại, không mất tiền.
- `order_code` lấy từ sequence `payments_order_code_seq` (bắt đầu 100000). payOS nhớ mọi mã từng dùng trong tài khoản, nên nếu DB bị tạo lại hoặc hai môi trường dùng chung khoá thì mã có thể trùng và payOS từ chối (xem mục 7).

---

## 5. Bảng mã lỗi <a id="5-bang-ma-loi"></a>

| Mã lỗi kỹ thuật | HTTP Status | Ý nghĩa |
| :--- | :---: | :--- |
| `PREMIUM_ALREADY_ACTIVE` | 409 | Gói premium còn hạn, hết hạn mới mua thêm |
| `VALIDATION_ERROR` | 400 | `plan_code` thiếu hoặc không hợp lệ |
| `NOT_FOUND` | 404 | Không có đơn, hoặc đơn thuộc người khác (dùng chung mã `NOT_FOUND` để không lộ đơn có tồn tại) |
| `PAYMENT_PROVIDER_ERROR` | 502 | payOS lỗi, từ chối tạo link (VD mã đơn đã tồn tại), huỷ link không hiệu lực, hoặc đơn đã nhận một phần tiền |
| `PAYMENT_NOT_CONFIGURED` | 503 | Chưa bật hoặc chưa cấu hình khoá payOS |
| `PAYMENT_WEBHOOK_INVALID` | 400 | Webhook sai chữ ký hoặc sai định dạng |
| `REQUEST_IN_PROGRESS` | 409 | Yêu cầu trước đó đang được xử lý hoặc đang tranh chấp khoá, vui lòng thử lại sau |

---

## 6. Cấu hình <a id="6-cau-hinh"></a>

Biến môi trường (`.env`, mẫu ở `.env.example`):

| Biến | Bắt buộc | Ý nghĩa |
| :--- | :---: | :--- |
| `PAYOS_ENABLED`, `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY` | Có | Khoá payOS |
| `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL` | **Có** | URL payOS chuyển hướng user về; thiếu thì app không khởi động. Nếu trỏ về server thì phải gồm context-path `API_PATH`, VD `http://localhost:8080/v1/public/payments/payos/return` |
| `PAYOS_EXPIRED_MINUTES` | Không (15) | Thời hạn thanh toán của một đơn (tối thiểu 1 phút) |
| `PAYMENT_WEBHOOK_LOG_RETENTION_DAYS` | Không (30) | Giữ nhật ký webhook bao nhiêu ngày (job dọn lúc 03:30 hằng ngày) |
| `PAYMENT_WEBHOOK_MISSING_MINUTES` | Không (10) | Đơn `PAID` quá bấy nhiêu phút mà chưa có webhook xác nhận thì dashboard cảnh báo |
| `PREMIUM_PRICE_1M`, `PREMIUM_PRICE_6M`, `PREMIUM_PRICE_12M` | **Có** | Giá gói (VND, số nguyên >= 2000); thiếu thì app không khởi động |

---

## 7. Chạy trên máy dev & xử lý sự cố <a id="7-su-co"></a>

**Webhook không tới `localhost`.** payOS ở internet không gọi được `localhost:8080`, nên khi chạy trên máy dev, thanh toán xong user **chưa** tự lên premium qua webhook. Hai cách:
1. Mở URL công khai bằng ngrok (`ngrok http 8080`) và đăng ký trong dashboard payOS (mục Kênh thanh toán → Webhook URL): `https://<địa-chỉ-ngrok>/v1/public/payments/payos/webhook`. Địa chỉ ngrok bản miễn phí đổi mỗi lần bật lại, phải đăng ký lại.
2. Hoặc gọi `GET /payments/{order_code}` (polling): server hỏi payOS và ghi `PAID`, nâng cấp user. Chỉ có tác dụng khi tiền **thật sự đã được chuyển** (payOS không có chế độ giả lập tiền).

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
| :--- | :--- | :--- |
| Trang return/cancel báo `404` của Tomcat | `PAYOS_RETURN_URL`/`PAYOS_CANCEL_URL` thiếu context-path (`/v1`) | Sửa `.env` thành `.../v1/public/payments/payos/return` và `.../cancel`, khởi động lại, tạo đơn mới (link cũ giữ URL cũ) |
| Trả tiền xong nhưng user chưa lên premium | Webhook chưa tới (chạy local, hoặc chưa đăng ký URL) | Gọi `GET /payments/{order_code}` bằng token của đúng user tạo đơn; hoặc cấu hình webhook như trên |
| `POST /users/me/premium-upgrades` trả `502` và log `Đơn thanh toán đã tồn tại` | Mã đơn từ sequence đã từng dùng trên payOS (DB bị tạo lại, hoặc môi trường khác dùng chung khoá) | Đẩy sequence lên cao: `ALTER SEQUENCE payments_order_code_seq RESTART WITH 200000;`; mỗi môi trường dùng khoá payOS riêng |
| `GET /payments/{order_code}` trả `404` | Đơn không tồn tại (DB đã tạo lại) hoặc token thuộc user khác | Tạo đơn mới rồi dùng `order_code` trong response; kiểm tra token |
| Log `[PAYMENT-RECONCILE]` | Có tiền về nhưng server không giao được hàng (thiếu tiền, đơn đã đóng, link mồ côi, link không khớp) | Đối chiếu với giao dịch trong dashboard payOS và xử lý tay |

---

## 8. Dashboard webhook (admin) <a id="8-dashboard-webhook"></a>

Mỗi lần payOS gọi `POST /public/payments/payos/webhook` (thành công lẫn lỗi) đều được ghi vào bảng `payment_webhook_logs`, để admin xem payOS gọi lúc nào và lỗi gì. **Trang web:** `/v1/ui/admin/webhooks.html` (menu "Webhook thanh toán" trong khu quản trị); dữ liệu lấy từ hai API dưới đây, chỉ quản trị viên đọc được (`403` với user thường, `401` khi chưa đăng nhập). Trang tự làm mới mỗi 15 giây.

Nhật ký **không** lưu body gốc, tên hay số tài khoản người chuyển; chỉ lưu `order_code`, `payment_link_id`, số tiền, mã payOS, kết quả, thông điệp ngắn, HTTP status, thời gian xử lý và IP.

### 8.1 Các loại kết quả (`result`)

| `result` | Lỗi? | Ý nghĩa |
| :--- | :---: | :--- |
| `PROCESSED` | Không | Đủ tiền, đơn `PAID`, đã giao hàng |
| `ALREADY_PAID` | Không | payOS gửi lại cho đơn đã `PAID` |
| `IGNORED_NOT_SUCCESS` | Không | payOS báo không thành công, bỏ qua |
| `IGNORED_EMPTY` | Không | Không có dữ liệu đơn, hoặc `order_code` < 100000 (webhook thử khi đăng ký URL) |
| `ORDER_NOT_FOUND` | **Có** | Có tiền về cho `order_code` thuộc dải của hệ thống nhưng không có đơn (link mồ côi?) |
| `LINK_MISMATCH` | **Có** | `payment_link_id` không khớp link đã lưu của đơn |
| `ORDER_CLOSED` | **Có** | Đơn đã `CANCELLED`/`EXPIRED` mà vẫn nhận tiền, không giao hàng |
| `UNDERPAID` | **Có** | Số tiền thu nhỏ hơn số đơn, không giao hàng |
| `INVALID_BODY` | **Có** | Body không phải JSON hợp lệ (HTTP 400) |
| `INVALID_SIGNATURE` | **Có** | Sai chữ ký hoặc dữ liệu bị sửa (HTTP 400); `order_code`/số tiền là giá trị bên gửi tự khai, chưa xác thực |
| `ERROR` | **Có** | Lỗi bất ngờ khi xử lý (rollback, payOS chưa cấu hình...), HTTP 5xx; vẫn được ghi dù transaction xử lý bị rollback |

Chống đầy bảng khi bị gửi webhook giả hàng loạt: với `INVALID_BODY`/`INVALID_SIGNATURE` ghi tối đa 100 dòng mỗi phút; phần vượt được **gộp vào một dòng mỗi phút** (`aggregated = true`, `occurrences` là số request đã gộp), không bị bỏ.

### 8.2 Danh sách nhật ký

- `GET /payments/admin/webhook-logs?result=&only_failed=false&order_code=&from_date=&to_date=&page=1&page_size=20`
- `only_failed=true` chỉ lấy nhóm lỗi trong bảng trên. `from_date`/`to_date` dạng `yyyy-MM-dd` (giờ Việt Nam), `to_date` tính cả ngày đó. Mới nhất trước (bỏ qua tham số sort).
- Response: `data.items` + `data.meta` (`page`, `page_size`, `total_items`, `total_pages`). Mỗi phần tử:
```json
{
  "id": "6f1c...",
  "received_at": "2026-10-20T10:03:11Z",
  "order_code": 200001,
  "payment_link_id": "8d84f6512e03...",
  "amount": 3000,
  "payos_code": "00",
  "success": true,
  "result": "PROCESSED",
  "message": "Đã giao hàng",
  "http_status": 200,
  "duration_ms": 42,
  "client_ip": "203.0.113.7",
  "occurrences": 1,
  "aggregated": false
}
```

### 8.3 Thống kê

- `GET /payments/admin/webhook-logs/summary?hours=24` (`hours` ngoài 1..168 được đưa về mốc gần nhất).
```json
{
  "success": true,
  "data": {
    "hours": 24,
    "total": 10,
    "failed": 2,
    "failure_rate": 0.2,
    "counts_by_result": { "PROCESSED": 6, "ALREADY_PAID": 2, "INVALID_SIGNATURE": 1, "UNDERPAID": 1, "...": 0 },
    "last_success_at": "2026-10-20T10:03:11Z",
    "last_failure_at": "2026-10-20T09:40:02Z",
    "paid_without_webhook": [200005, 200003],
    "missing_minutes": 10
  },
  "msg": ""
}
```
- `last_success_at`/`last_failure_at` không bị giới hạn bởi `hours`.
- `paid_without_webhook`: `order_code` các đơn `PAID` trong khoảng `hours` mà `paid_at` đã quá `PAYMENT_WEBHOOK_MISSING_MINUTES` phút **và không có dòng `PROCESSED`/`ALREADY_PAID` nào** cho đơn đó (đơn được ghi `PAID` qua polling hoặc job còn webhook thì không tới), tối đa 50. Đơn `PENDING` không bao giờ nằm trong danh sách này (đơn chưa trả vốn không có webhook).
