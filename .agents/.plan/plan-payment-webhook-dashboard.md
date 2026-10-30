# Trang dashboard HTML cho admin xem webhook payOS lỗi khi nào

## Bối cảnh

- Webhook là đường chính để nâng cấp user sau khi thanh toán (`PaymentController.java` `POST /public/payments/payos/webhook` → `PaymentServiceImpl.processWebhook`). Hiện mọi kết quả của webhook chỉ nằm trong **file log**: sai chữ ký, không tìm thấy đơn, link không khớp, thiếu tiền, đơn đã đóng... không có chỗ nào xem lại được "payOS gọi lúc nào, lỗi gì".
- Khi webhook hỏng (sai URL, sai khoá, lỗi xử lý), user trả tiền nhưng chưa lên premium và bạn chỉ biết khi user than phiền.
- Yêu cầu của user: **một trang HTML riêng, chỉ admin xem được**, hiển thị các lần payOS gọi webhook và lỗi xảy ra khi nào.
- Dự án đã có sẵn khung trang admin tĩnh: [users.html](../../src/main/resources/static/ui/admin/users.html) + [users.js](../../src/main/resources/static/ui/admin/js/users.js), dùng chung [api.js](../../src/main/resources/static/ui/assets/js/api.js) (JWT trong `localStorage`, tự gắn `Authorization`) và [layout.js](../../src/main/resources/static/ui/assets/js/layout.js) (menu `NAV.admin`, chặn khi chưa đăng nhập). Trang mới đi theo đúng khung này.

## Quyết định đã chốt

| # | Nội dung |
|---|---|
| 1 | Giao diện là **một trang HTML riêng** trong `ui/admin/` (user chốt), chỉ admin xem được dữ liệu. |
| 2 | "Chỉ admin xem được" được bảo đảm ở **API** (`@PreAuthorize` quyền `PAYMENT_READ` hoặc `ROLE_ADMIN`, giống `PaymentAdminController`), vì file HTML tĩnh không chứa dữ liệu và không thể tự chặn truy cập (JWT nằm trong header, trình duyệt không gửi khi mở URL trang). Trang cũng chuyển hướng về đăng nhập nếu chưa có token và hiện "Không có quyền" khi API trả `403`, như các trang admin khác. |
| 3 | Lưu **mỗi lần payOS gọi webhook** vào bảng mới `payment_webhook_logs` (ghi cả thành công lẫn lỗi để có số liệu "lỗi trên tổng số"), kết quả phân loại bằng enum `WebhookResult`. |
| 4 | **Không lưu body gốc** và không lưu thông tin ngân hàng/tên người chuyển (có trong `WebhookData`) để tránh dữ liệu cá nhân: chỉ lưu `order_code`, `payment_link_id`, `amount`, `code`, `success`, kết quả, thông điệp ngắn, `http_status`, thời gian xử lý, IP. |
| 5 | Ghi log phải bền: dùng transaction riêng (`REQUIRES_NEW`) để vẫn lưu khi xử lý webhook bị rollback, và **không bao giờ làm hỏng phản hồi webhook** (lỗi khi ghi log chỉ ghi vào file log). |
| 6 | Controller không chứa logic: thêm service `PaymentWebhookService.handle(rawBody, clientIp)` làm toàn bộ (parse, xử lý, ghi nhận, ném lại lỗi); controller chỉ gọi một hàm. |
| 7 | Tự dọn: job xoá bản ghi cũ hơn `PAYMENT_WEBHOOK_LOG_RETENTION_DAYS` ngày (mặc định 30), theo mẫu `IdempotencyCleanupJob`. |
| 8 | Chống đầy bảng khi bị spam webhook giả: với `INVALID_SIGNATURE`/`INVALID_BODY` ghi tối đa `100` dòng mỗi phút; phần vượt **không bị bỏ** mà **gộp thành một dòng mỗi phút** (`message` = "còn N request giả mạo bị gộp", cập nhật bộ đếm) nên vẫn nằm trong DB và xem được qua API/trang (user chốt: mọi thứ lưu DB rồi gọi API xem). |
| 9 | Có viết test (user chốt). |
| 10 | Giữ log 30 ngày (`PAYMENT_WEBHOOK_LOG_RETENTION_DAYS`, user chốt "như đề xuất"). |
| 11 | **Cảnh báo "webhook có thể không tới"** (user chốt ngưỡng 10 phút, cấu hình `.env`: `PAYMENT_WEBHOOK_MISSING_MINUTES`, mặc định 10): trang và `summary` báo khi có đơn `PAID` mà `paid_at` đã quá ngưỡng và **không có dòng webhook `PROCESSED` hoặc `ALREADY_PAID` nào cho `order_code` đó** (đơn được ghi `PAID` qua polling/job còn webhook thì trượt). Không dùng quy tắc "đơn PENDING lâu không có webhook" vì đơn chưa trả vốn không có webhook nên sẽ báo động giả. Không đổi bảng `payments`. |
| 12 | `ORDER_NOT_FOUND` và `UNDERPAID` là **kết quả của một lần gọi webhook** (không phải trạng thái đơn), đều thuộc nhóm lỗi (tiền đã về mà chưa giao được hàng). |

## 1. Các file sẽ thay đổi

Đường dẫn gốc `src/main/java/com/datn/financeapp/`, trừ khi ghi khác.

**CSDL**

| File | Thay đổi |
|---|---|
| `src/main/resources/db/migration_v2/V18__payment_webhook_logs.sql` | **Mới.** Bảng `payment_webhook_logs` (`id`, `received_at`, `order_code`, `payment_link_id`, `amount`, `payos_code`, `success`, `result`, `message`, `http_status`, `duration_ms`, `client_ip`), index `(received_at DESC)` và index một phần cho các dòng lỗi, `CHECK` cho `result`. ⚠️ Số version sau `V17`; **Giai đoạn 2 của plan huỷ đơn đổi từ `V18` sang `V19`**. |

**Code — module `pay`**

| File | Thay đổi |
|---|---|
| `module/pay/enums/WebhookResult.java` | **Mới.** `PROCESSED`, `ALREADY_PAID`, `IGNORED_NOT_SUCCESS`, `IGNORED_EMPTY`, `ORDER_NOT_FOUND`, `LINK_MISMATCH`, `ORDER_CLOSED`, `UNDERPAID`, `INVALID_BODY`, `INVALID_SIGNATURE`, `ERROR`; hàm `isFailure()`. |
| `module/pay/entity/PaymentWebhookLog.java` | **Mới.** Entity chỉ ghi thêm (append-only), không kế thừa `AssignedIdEntity` vì không cần `updated_at`/xoá mềm. |
| `module/pay/repository/PaymentWebhookLogRepository.java` | **Mới.** `JpaSpecificationExecutor`, truy vấn thống kê theo `result`, `lastSuccessAt`, xoá theo hạn, đếm lỗi trong một phút và gộp vào một dòng khi vượt giới hạn (chống spam), truy vấn `paid_without_webhook` (join `payments` với log theo `order_code`). |
| `module/pay/dto/res/WebhookOutcome.java` | **Mới.** `record(WebhookResult result, Long orderCode, String paymentLinkId, Long amount, String message)` — kết quả mà `processWebhook` trả về. |
| `module/pay/service/PaymentService.java` + `service/impl/PaymentServiceImpl.java` | **Sửa** `processWebhook` từ `void` thành trả `WebhookOutcome` (mỗi nhánh hiện có `return` trả đúng một `WebhookResult`). Logic nghiệp vụ **không đổi**. |
| `module/pay/service/PaymentWebhookService.java` + `service/impl/PaymentWebhookServiceImpl.java` | **Mới.** `handle(rawBody, clientIp)`: parse body (hỏng → `INVALID_BODY` + ném `PAYMENT_WEBHOOK_INVALID`), gọi `processWebhook`, bắt `PaymentException` sai chữ ký → `INVALID_SIGNATURE`, ngoại lệ khác → `ERROR`; đo thời gian; gọi recorder; ném lại lỗi gốc. Chuyển `parseWebhook` và `PAYOS_MAPPER` từ controller sang đây. |
| `module/pay/service/PaymentWebhookLogRecorder.java` (+ impl) | **Mới.** Ghi một dòng log với `@Transactional(propagation = REQUIRES_NEW)`, bắt mọi lỗi, áp giới hạn chống spam. |
| `module/pay/service/PaymentWebhookLogAdminService.java` (+ impl) | **Mới.** `list(filter, pageable)` và `summary(hours)`. |
| `module/pay/dto/res/WebhookLogRes.java`, `WebhookLogListRes.java`, `WebhookSummaryRes.java` | **Mới.** Response danh sách và thống kê. |
| `module/pay/mapper/PaymentWebhookLogMapper.java` | **Mới.** MapStruct, `unmappedTargetPolicy = ERROR`. |
| `module/pay/controller/PaymentController.java` | **Sửa.** `payOsWebhook` chỉ còn `webhookService.handle(body, clientIp)`; bỏ `parseWebhook`/`PAYOS_MAPPER` khỏi controller. Lấy IP bằng `ClientIpResolver` có sẵn (`common/security/ClientIpResolver.java`). |
| `module/pay/controller/PaymentAdminController.java` | **Thêm** `GET /payments/admin/webhook-logs` và `GET /payments/admin/webhook-logs/summary` (cùng `PERM_READ`). Lưu ý đường dẫn cố định phải đứng trước `/{order_code}`. |
| `module/pay/scheduler/PaymentWebhookLogCleanupJob.java` | **Mới.** 03:30 mỗi ngày, giờ VN. |
| `module/pay/config/PaymentWebhookLogProperties.java` | **Mới.** `retentionDays` (`@Positive`, mặc định 30), `missingMinutes` (`@Positive`, mặc định 10). |
| `application.yml`, `.env.example` | Thêm `payos.webhook-log.retention-days: ${PAYMENT_WEBHOOK_LOG_RETENTION_DAYS:30}` và `payos.webhook-log.missing-minutes: ${PAYMENT_WEBHOOK_MISSING_MINUTES:10}`. |

**Giao diện (`src/main/resources/static/ui/`)**

| File | Thay đổi |
|---|---|
| `admin/webhooks.html` | **Mới.** Theo khung `users.html` (`data-area="admin" data-active="webhooks"`). |
| `admin/js/webhooks.js` | **Mới.** Gọi API bằng `api()` có sẵn, tự làm mới 15 giây, có nút tạm dừng. |
| `assets/js/layout.js` | **Sửa** thêm `['webhooks', '🔔', 'Webhook thanh toán']` vào `NAV.admin`. |

**Test** (dưới `src/test/java/com/datn/financeapp/`): `unitTest/pay/service/impl/PaymentWebhookServiceImplTest.java`, `unitTest/pay/service/impl/PaymentWebhookLogRecorderTest.java`, `unitTest/pay/service/impl/PaymentWebhookLogAdminServiceImplTest.java`, `unitTest/pay/scheduler/PaymentWebhookLogCleanupJobTest.java`, `integrationTest/pay/controller/PaymentWebhookLogIntegrationTest.java`, `integrationTest/pay/repository/PaymentWebhookLogRepositoryTest.java`; sửa `unitTest/pay/service/impl/PaymentServiceImplTest.java` (thêm khẳng định `WebhookOutcome`).

**Tài liệu / Bruno**: `docs/api/payment.md` (mục webhook log và trang dashboard), `docs/work_flow/payment.md` (thêm bước ghi nhận), `docs/work_flow/scheduler.md` (job dọn), `bruno/15-Payments/Admin/03-Webhook-Logs.yml`, `04-Webhook-Summary.yml`.

**Không đổi**: logic nghiệp vụ trong `markPaid`, `closeUnpaid`, `syncPending`; `PayOsClientHelper`.

## 2. Lý do thay đổi

- Không quan sát được webhook: mọi kết quả chỉ là `log.info/error` rải rác trong `processWebhook` và `PaymentController` (`PaymentServiceImpl.java` các nhánh `return`), không truy vấn được theo thời gian hay theo loại lỗi.
- Phải ghi nhận **cả khi xử lý rollback**: lỗi nâng cấp làm rollback cả transaction của `processWebhook`, nếu ghi log cùng transaction thì chính lúc lỗi lại mất dấu vết. Vì vậy cần transaction riêng và nằm ngoài `processWebhook`.
- Rule 2.4 (`ApiResponse`, `PageMeta`), 2.8 (đường dẫn `kebab-case`), 2.9 (`@PreAuthorize` ở controller), 2.7 (MapStruct `ERROR`), 2.12 (lỗi qua `BusinessException`), quy tắc "không đặt business logic ở Controller" (`converstation-rule.md`).

## 3. Hướng giải quyết / code xem trước

**Bảng và kết quả**

```sql
CREATE TABLE payment_webhook_logs (
    id              UUID PRIMARY KEY,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    order_code      BIGINT,
    payment_link_id VARCHAR(100),
    amount          BIGINT,
    payos_code      VARCHAR(10),
    success         BOOLEAN,
    result          VARCHAR(30) NOT NULL,
    message         VARCHAR(500),
    http_status     INT NOT NULL,
    duration_ms     INT NOT NULL,
    client_ip       VARCHAR(64),
    CONSTRAINT ck_pwl_result CHECK (result IN ('PROCESSED','ALREADY_PAID','IGNORED_NOT_SUCCESS','IGNORED_EMPTY',
        'ORDER_NOT_FOUND','LINK_MISMATCH','ORDER_CLOSED','UNDERPAID','INVALID_BODY','INVALID_SIGNATURE','ERROR'))
);
CREATE INDEX idx_pwl_received ON payment_webhook_logs (received_at DESC);
CREATE INDEX idx_pwl_failed ON payment_webhook_logs (received_at DESC)
    WHERE result IN ('ORDER_NOT_FOUND','LINK_MISMATCH','ORDER_CLOSED','UNDERPAID','INVALID_BODY','INVALID_SIGNATURE','ERROR');
```

Lỗi (`isFailure()`) là: `INVALID_BODY`, `INVALID_SIGNATURE`, `ORDER_NOT_FOUND`, `LINK_MISMATCH`, `ORDER_CLOSED`, `UNDERPAID`, `ERROR`. Không phải lỗi: `PROCESSED`, `ALREADY_PAID`, `IGNORED_NOT_SUCCESS`, `IGNORED_EMPTY`.

**Luồng ghi nhận**

```java
// PaymentWebhookServiceImpl — KHÔNG @Transactional (để transaction của processWebhook tự rollback độc lập)
public void handle(String rawBody, String clientIp) {
    long started = System.nanoTime();
    WebhookOutcome outcome = null;
    int httpStatus = 200;
    RuntimeException failure = null;
    try {
        Webhook webhook = parse(rawBody);                         // hỏng → PaymentException(PAYMENT_WEBHOOK_INVALID)
        outcome = paymentService.processWebhook(webhook);         // transaction riêng của nghiệp vụ
    } catch (PaymentException ex) {
        failure = ex; httpStatus = ex.getHttpStatus();
        outcome = WebhookOutcome.invalid(ex);                     // INVALID_BODY hoặc INVALID_SIGNATURE
    } catch (RuntimeException ex) {
        failure = ex; httpStatus = 500;
        outcome = WebhookOutcome.error(ex);                       // ERROR + thông điệp ngắn
    } finally {
        recorder.record(outcome, httpStatus, elapsedMs(started), clientIp);   // REQUIRES_NEW, nuốt mọi lỗi
    }
    if (failure != null) throw failure;                           // giữ nguyên phản hồi cũ (400/5xx)
}
```

```java
// PaymentController — chỉ một lời gọi
public ApiResponse<Void> payOsWebhook(@RequestBody String body, HttpServletRequest request) {
    paymentWebhookService.handle(body, clientIpResolver.resolve(request));
    return ApiResponse.of(null);
}
```

**API admin (⚠️ endpoint mới, chỉ đọc)**

| Method | Path | Quyền | Ghi chú |
|---|---|---|---|
| GET | `/payments/admin/webhook-logs` | `PAYMENT_READ` hoặc `ROLE_ADMIN` | `?result=&only_failed=true&order_code=&from=&to=&page=&page_size=`, mới nhất trước. |
| GET | `/payments/admin/webhook-logs/summary` | như trên | `?hours=24` (1–168): tổng số, số lỗi, `failure_rate`, đếm theo `result`, `last_success_at`, `last_failure_at`, `paid_without_webhook` (danh sách `order_code` đơn `PAID` quá ngưỡng mà không có webhook xác nhận, tối đa 50). |

**Trang HTML** (`ui/admin/webhooks.html`):
- Thanh số liệu đầu trang (24 giờ qua): tổng, lỗi, tỉ lệ lỗi, lần thành công gần nhất, lần lỗi gần nhất; mỗi ô đếm theo loại là nút bấm để lọc.
- Bảng: thời gian (giờ VN), kết quả (nhãn màu, lỗi màu đỏ), `order_code` (bấm để mở chi tiết đơn qua `/payments/admin/{order_code}`), số tiền, `payment_link_id`, HTTP, thời gian xử lý, thông điệp.
- Mặc định chỉ hiện **lỗi**; ô chọn "Tất cả"; lọc theo khoảng thời gian; tự làm mới 15 giây, có nút tạm dừng.
- Cảnh báo ngay đầu trang khi có đơn `PAID` quá `PAYMENT_WEBHOOK_MISSING_MINUTES` phút (mặc định 10) mà không có webhook xác nhận (quyết định #11), kèm danh sách `order_code` để bấm xem; không có thì ẩn.

**Test viết đỏ trước (đỏ vì khẳng định sai ở):**
- `PaymentWebhookServiceImplTest`: mỗi nhánh của `processWebhook` map đúng `WebhookResult` (đỏ vì hiện chưa có enum/recorder); sai chữ ký → ghi `INVALID_SIGNATURE`, vẫn ném `PAYMENT_WEBHOOK_INVALID`; body hỏng → `INVALID_BODY`; `processWebhook` ném `RuntimeException` → ghi `ERROR` rồi ném lại; **recorder ném lỗi thì phản hồi webhook vẫn như cũ**; thời gian xử lý được đo.
- `PaymentWebhookLogRecorderTest`: giới hạn 100 dòng/phút cho `INVALID_*`, phần vượt **gộp vào một dòng** (không mất, số gộp đúng); lỗi DB khi ghi không ném ra ngoài; không lưu body/PII.
- `PaymentWebhookLogAdminServiceImplTest` / repository test: `paid_without_webhook` chỉ chứa đơn `PAID` quá ngưỡng **và** không có dòng `PROCESSED`/`ALREADY_PAID`; đơn vừa `PAID` chưa quá 10 phút không bị liệt kê; đơn `PENDING` không bao giờ bị liệt kê (chứng minh không báo động giả); đơn đã có webhook thì không bị liệt kê.
- `PaymentWebhookLogIntegrationTest` (PostgreSQL thật): webhook hợp lệ → một dòng `PROCESSED`; gửi lại → `ALREADY_PAID`; sai chữ ký → dòng `INVALID_SIGNATURE` **dù HTTP trả 400**; nâng cấp lỗi làm rollback → dòng `ERROR` **vẫn được lưu** (chứng minh `REQUIRES_NEW`); đơn lạ ≥ 100000 → `ORDER_NOT_FOUND`; user thường gọi API → `403`, không token → `401`, admin → `200` có phân trang; lọc `only_failed`, `result`, khoảng thời gian; `summary` đếm đúng.
- `PaymentWebhookLogCleanupJobTest` + repository test: chỉ xoá dòng cũ hơn hạn.
- Giao diện: không có test tự động trong dự án cho các trang `ui/`; kiểm tra thủ công theo các bước ở mục "Kiểm tra".

## 4. Danh sách công việc chi tiết

1. (Đã chốt hết câu hỏi mở.)
2. Viết test đỏ (unit rồi integration) cho service, recorder, admin service, job.
3. `V18`, entity, enum, repository, mapper, DTO.
4. Đổi `processWebhook` trả `WebhookOutcome` (không đổi nghiệp vụ), sửa `PaymentServiceImplTest`.
5. `PaymentWebhookService`, recorder, sửa `PaymentController`.
6. `PaymentWebhookLogAdminService`, hai endpoint admin.
7. `PaymentWebhookLogCleanupJob`, cấu hình, `.env.example`.
8. Trang `webhooks.html`, `webhooks.js`, mục menu trong `layout.js`.
9. Tài liệu, Bruno, đánh dấu số migration của plan huỷ đơn (Giai đoạn 2 → `V19`).
10. Biên dịch, `git diff` đối chiếu mục 1, báo cáo.

## Ngoài phạm vi

- Cảnh báo chủ động (email/push/Telegram) khi webhook lỗi; chỉ có trang xem.
- Lưu body gốc của webhook (có dữ liệu cá nhân của người chuyển tiền).
- Gửi lại (replay) webhook từ trang; trang chỉ đọc.
- Dashboard cho các loại sự kiện khác (không phải payOS).
- ⚠️ `application-dev.yml` vẫn chứa khoá payOS/Cloudinary mặc định (đã nêu ở plan trước); không sửa ở đây.

## Kiểm tra

- Hỏi bạn trước khi chạy test; không chạy full test.
- Lệnh chạy test liên quan (integration cần Docker):

```powershell
mvn test -Dtest=PaymentWebhookServiceImplTest,PaymentWebhookLogRecorderTest,PaymentWebhookLogAdminServiceImplTest,PaymentWebhookLogCleanupJobTest,PaymentServiceImplTest
```

```powershell
mvn test -Dtest=PaymentWebhookLogIntegrationTest,PaymentWebhookLogRepositoryTest,PaymentWebhookIntegrationTest
```

- Kiểm tra trang bằng tay (sau khi chạy server): đăng nhập admin, mở `http://localhost:8080/v1/ui/admin/webhooks.html`; gọi `POST /public/payments/payos/webhook` với body rác bằng Bruno và thấy dòng `INVALID_BODY` hiện ra trong tối đa 15 giây; đăng nhập user thường thì trang báo không có quyền và API trả `403`.
- `rg -n "parseWebhook|PAYOS_MAPPER" src/main/java` chỉ còn trong `PaymentWebhookServiceImpl`.
- Bạn tự chạy full test nếu cần kết quả tổng.

## Câu hỏi còn mở

Không còn (đã chốt: viết test, giữ 30 ngày, gộp thay vì bỏ khi bị spam, ngưỡng cảnh báo 10 phút trong `.env`, `ORDER_NOT_FOUND`/`UNDERPAID` là lỗi).

## Ghi chú khi thực hiện (lệch nhỏ so với plan)

- `ClientIpResolver` là lớp tiện ích tĩnh (không phải bean) nên controller gọi `ClientIpResolver.resolve(request)`.
- Thêm cột `occurrences` và `aggregated` vào `payment_webhook_logs` để gộp request giả mạo vượt giới hạn mà không mất dữ liệu (quyết định #8).
- `from`/`to` của API đặt tên `from_date`/`to_date` cho khớp `GET /payments/admin`.
- Trang web không có test tự động (dự án không có test cho `ui/`), kiểm tra tay theo mục "Kiểm tra".
- Quy tắc cảnh báo thiếu webhook là "đơn PAID quá ngưỡng không có webhook xác nhận" (quyết định #11), không phải "đơn PENDING lâu".
- Giai đoạn 2 của plan huỷ đơn đổi số migration từ `V18` sang `V19`.

## Bảng công việc

| # | Công việc | Trạng thái |
|---|---|---|
| 1 | Chốt câu hỏi mở | Hoàn thành |
| 2 | Test đỏ | Hoàn thành (đã biên dịch, chưa chạy) |
| 3 | `V18`, entity, enum, repository, mapper, DTO | Hoàn thành |
| 4 | `processWebhook` trả `WebhookOutcome` | Hoàn thành |
| 5 | `PaymentWebhookService`, recorder, controller | Hoàn thành |
| 6 | Admin service và hai endpoint | Hoàn thành |
| 7 | Job dọn, cấu hình | Hoàn thành |
| 8 | Trang HTML, JS, menu | Hoàn thành |
| 9 | Tài liệu, Bruno, đổi số migration Giai đoạn 2 | Hoàn thành |
| 10 | Biên dịch, đối chiếu diff, báo cáo | Hoàn thành |
