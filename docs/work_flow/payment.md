# Workflow — Thanh toán & nâng cấp Premium (pay, user)

Code chính: [PaymentServiceImpl](../../src/main/java/com/datn/financeapp/module/pay/service/impl/PaymentServiceImpl.java),
[PayOsClientHelper](../../src/main/java/com/datn/financeapp/module/pay/helper/PayOsClientHelper.java),
[PaymentController](../../src/main/java/com/datn/financeapp/module/pay/controller/PaymentController.java),
[UserServiceImpl](../../src/main/java/com/datn/financeapp/module/user/service/impl/UserServiceImpl.java) (`createPremiumUpgrade`, `upgradeToPremium`),
[PremiumPaymentListener](../../src/main/java/com/datn/financeapp/module/user/event/Listener/PremiumPaymentListener.java).

Đặc tả API: [payment.md](../api/payment.md). Migration: [V16](../../src/main/resources/db/migration_v2/V16__payments_and_premium.sql) (bảng `payments`, sequence `payments_order_code_seq`, cột `users.plan_expires_at`, quyền `PAYMENT_READ`) và [V17](../../src/main/resources/db/migration_v2/V17__payment_amount_paid.sql) (cột `payments.amount_paid`).

Hướng phụ thuộc giữa module: `user → pay` (user gọi `PaymentService`, nghe `PaymentPaidEvent`). `pay` không biết gì về premium, nên thêm loại hàng hoá khác sau này chỉ cần thêm giá trị `PaymentType` và listener của module đó.

## Trạng thái đơn

| Trạng thái | Ý nghĩa | Chuyển từ |
|---|---|---|
| `PENDING` | Đang chờ thanh toán, link payOS còn sống | tạo đơn |
| `PAID` | Đã nhận đủ tiền, đã giao hàng | `PENDING` |
| `CANCELLED` | User huỷ, hoặc bị thay bằng đơn mới | `PENDING` |
| `EXPIRED` | Quá hạn không thanh toán | `PENDING` |

Bất biến: chỉ ghi `CANCELLED`/`EXPIRED` **sau khi** payOS xác nhận link đã chết (và chưa nhận đồng nào), nên đơn đã đóng không thể nhận tiền. Mỗi user tối đa **một đơn `PENDING`** cho mỗi loại hàng hoá (unique index `uq_payments_pending_per_user_type`).

## Tạo đơn nâng cấp — `POST /users/me/premium-upgrades`

```
createPremiumUpgrade (UserServiceImpl, một transaction)
-> khoá dòng user (findByIdForUpdate)
-> đang PREMIUM còn hạn (hoặc premium không hạn) → PREMIUM_ALREADY_ACTIVE
-> giá = PremiumPlanProperties.priceOf(gói)   (client không gửi tiền)
-> PaymentService.createPayment(userId, PREMIUM_UPGRADE, tên gói, giá)
   -> khoá đơn PENDING của user kiểu NOWAIT (đang bị webhook/job giữ → REQUEST_IN_PROGRESS, 409)
   -> không có đơn PENDING → tạo đơn mới (bên dưới)
   -> có đơn PENDING → closeUnpaid (bên dưới), kết quả:
        đã nhận đủ tiền   → ghi PAID, trả đơn PAID, KHÔNG tạo đơn mới
        đã nhận một phần  → log ERROR, PAYMENT_PROVIDER_ERROR (502), giữ PENDING, không tạo đơn mới
        link chưa chết    → huỷ link payOS rồi đọc lại kết quả huỷ
                            chưa chắc đã chết → PAYMENT_PROVIDER_ERROR, giữ PENDING
        link đã chết      → đơn cũ = CANCELLED (EXPIRED nếu đã quá hạn) → flush → tạo đơn mới

tạo đơn mới
-> order_code = nextval(payments_order_code_seq)   (bắt đầu 100000)
-> hạn = bây giờ + PAYOS_EXPIRED_MINUTES
-> payOS.createLink(order_code, số tiền, mô tả "DH<mã>", hạn)
   -> payOS từ chối (VD "Đơn thanh toán đã tồn tại") → PAYMENT_PROVIDER_ERROR (502)
-> đăng ký huỷ link bù trừ nếu transaction rollback sau bước này (tránh link mồ côi)
-> insert payments (PENDING, payos_link_id)
-> trả { order_code, reference_id, amount, status, checkout_url, expires_at }
```

`@Idempotent` ở controller: gửi lại cùng `Idempotency-Key` thì nhận đúng response cũ, không chạy lại nghiệp vụ.

## closeUnpaid — đóng một đơn chưa trả

Dùng chung cho tạo đơn mới, huỷ đơn, polling khi quá hạn và job dọn đơn quá hạn.

```
closeUnpaid(đơn, trạng thái đích)
-> payOS.getLink
-> received = max(tổng các giao dịch của link, amountPaid của link)   (không đánh giá thấp tiền đã nhận)
-> received ≥ số đơn → markPaid
-> 0 < received < số đơn, hoặc link báo PAID mà thiếu số liệu
     → log ERROR [PAYMENT-RECONCILE], PAYMENT_PROVIDER_ERROR, đơn giữ PENDING
-> link chưa chết (không phải CANCELLED/EXPIRED/FAILED):
     payOS.cancelLink, đọc lại kết quả
        đủ tiền → markPaid
        có tiền một phần, hoặc huỷ chưa hiệu lực → PAYMENT_PROVIDER_ERROR, giữ PENDING
-> đơn = trạng thái đích   (chỉ tới đây khi chưa có đồng nào và link chắc chắn đã chết)
```

## Hỏi trạng thái — `GET /payments/{order_code}` (polling)

```
getStatus
-> đơn có thuộc user không (existsByOrderCodeAndUserId, không nạp entity) → không → NOT_FOUND
-> khoá đơn (findByOrderCodeForUpdate)      lần nạp đầu tiên, có khoá
-> đơn PENDING → syncPending:
     quá hạn → closeUnpaid(EXPIRED)
     payOS.getLink:
        received ≥ số đơn → markPaid
        0 < received < số đơn → log ERROR, giữ PENDING
        link CANCELLED/EXPIRED/FAILED và chưa có tiền → ghi CANCELLED hoặc EXPIRED tương ứng
     payOS lỗi → log, giữ PENDING (không ném lỗi, để client polling tiếp)
-> trả trạng thái
```

Đây cũng là đường dự phòng khi webhook chưa tới: gọi API này là server tự hỏi payOS và nâng cấp user nếu đã trả.

## Huỷ đơn — `DELETE /payments/{order_code}`

```
cancel
-> kiểm quyền sở hữu, khoá đơn (như getStatus)
-> đơn PENDING → closeUnpaid(CANCELLED)
     payOS báo đã trả đủ → ghi PAID (response có status = PAID, không phải CANCELLED)
     payOS lỗi / đã nhận một phần → PAYMENT_PROVIDER_ERROR (502)
-> đơn đã PAID/CANCELLED/EXPIRED → trả nguyên trạng thái, không lỗi (idempotent)
```

## Webhook payOS — `POST /public/payments/payos/webhook`

Controller chỉ gọi `PaymentWebhookService.handle(body, ip)` (không `@Transactional`). Hàm này parse body, gọi `PaymentService.processWebhook` (transaction riêng của nghiệp vụ), rồi **ghi một dòng `payment_webhook_logs` bằng transaction riêng (`REQUIRES_NEW`)** nên vẫn lưu kể cả khi nghiệp vụ bị rollback, sau đó ném lại đúng lỗi gốc (phản hồi HTTP không đổi). Lỗi khi ghi nhật ký chỉ ghi vào log file, không làm đổi phản hồi. Các loại kết quả và dashboard xem [payment.md mục 8](../api/payment.md#8-dashboard-webhook).

```
processWebhook
-> body parse bằng ObjectMapper riêng (payOS gửi camelCase, không đi qua SNAKE_CASE của Spring); hỏng → PAYMENT_WEBHOOK_INVALID
-> payOS.verifyWebhook (sai chữ ký → PAYMENT_WEBHOOK_INVALID, 400)
-> không phải success + code "00" ở cả webhook và data → bỏ qua
-> khoá đơn theo order_code
     không có đơn: order_code ≥ 100000 → log ERROR [PAYMENT-RECONCILE] (link mồ côi?), nhỏ hơn → INFO; trả 200
-> payos_link_id không khớp → log ERROR, bỏ qua
-> markPaid(số tiền webhook)
```

## markPaid — điều kiện ghi PAID

Kiểm tra theo thứ tự:

| Điều kiện | Kết quả |
|---|---|
| Đơn đã `PAID` (webhook gửi lại) | Bỏ qua, không giao lần hai |
| Đơn `CANCELLED`/`EXPIRED` | **Không đổi, không giao hàng**, log ERROR (bất biến bị vỡ) |
| Số tiền thu < số đơn (hoặc null) | Không giao, giữ `PENDING`, log ERROR |
| Số tiền thu = số đơn | `amount_paid` = số tiền thu, `PAID`, `paid_at`, phát `PaymentPaidEvent` |
| Số tiền thu > số đơn (trả dư) | Như trên, log INFO số dư; phần dư **không hoàn**, tra bằng `amount_paid`/`excess_amount` |

## Giao hàng — nâng cấp premium

`PaymentPaidEvent` được xử lý **đồng bộ, cùng transaction** với việc ghi `PAID`:

```
PremiumPaymentListener.onPaymentPaid
-> chỉ xử lý loại PREMIUM_UPGRADE
-> referenceId sai/rỗng → log ERROR, IllegalStateException (rollback cả việc ghi PAID, payOS gửi lại webhook)
-> UserService.upgradeToPremium(userId, số tháng của gói)
   -> khoá dòng user
   -> premium không hạn → bỏ qua, log
   -> còn hạn (hai đơn cùng được trả)  → hạn mới = hạn hiện tại + số tháng   (cộng dồn)
   -> chưa premium hoặc hết hạn         → hạn mới = bây giờ + số tháng
   -> users.plan = PREMIUM, plan_expires_at = hạn mới
-> user đã bị xoá → log ERROR [PAYMENT-RECONCILE], bỏ qua, KHÔNG ném lỗi (tránh payOS gửi lại mãi)
```

Access token cũ vẫn mang `plan = FREE` đến khi hết hạn token; user refresh token hoặc đăng nhập lại để nhận `PREMIUM`.

## Thứ tự khoá

Luôn khoá **đơn thanh toán trước, rồi mới tới user**. `createPremiumUpgrade` đi ngược (khoá user trước), nên bước khoá đơn trong `createPayment` dùng `NOWAIT` và đổi lỗi khoá sang `REQUEST_IN_PROGRESS` (409) thay vì chờ, để không thể deadlock với webhook (webhook khoá đơn rồi mới khoá user khi nâng cấp).

## Tác vụ định kỳ

Xem [scheduler.md](scheduler.md): `PaymentExpiryJob` (dọn đơn quá hạn, mỗi 5 phút), `PremiumExpiryJob` (hạ premium hết hạn, 00:05 hằng ngày) và `PaymentWebhookLogCleanupJob` (dọn nhật ký webhook cũ, 03:30 hằng ngày).

## Chạy trên máy dev

payOS không gọi được `localhost`, nên webhook không tới. Dùng ngrok và đăng ký URL `.../v1/public/payments/payos/webhook` trong dashboard payOS, hoặc gọi `GET /payments/{order_code}` để server tự hỏi payOS (cần tiền thật đã chuyển). `PAYOS_RETURN_URL`/`PAYOS_CANCEL_URL` phải có context-path (`/v1`). Chi tiết và các lỗi hay gặp xem [payment.md mục 7](../api/payment.md#7-su-co).

## Việc chưa làm / lỗi đang mở

- **Trả thiếu tiền** chưa có luồng bù tiền: đơn đã nhận một phần giữ `PENDING`, không đóng được và chặn tạo đơn mới cho tới khi xử lý tay (đã có plan Giai đoạn 2 cho phần bù trong thời hạn, chưa code).
- Chưa có job nền tự hỏi payOS định kỳ cho đơn `PENDING`: nếu webhook không tới và client không polling thì đơn chỉ được xử lý khi quá hạn (`PaymentExpiryJob`).
- Trang `return` chỉ hiển thị HTML, không xác nhận với payOS và không đổi dữ liệu.
- Chưa có API/job đối soát: các tình huống tiền về mà không giao được hàng chỉ để lại log `[PAYMENT-RECONCILE]`.
- Sinh `order_code` bằng sequence: DB bị tạo lại hoặc hai môi trường dùng chung khoá payOS thì có thể trùng mã đã dùng trên payOS (đang đề xuất đổi sang mã theo thời gian).
