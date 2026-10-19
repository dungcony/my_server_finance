# Xây API nâng cấp tài khoản FREE → PREMIUM qua payOS (bảng `payments` dùng chung)

## Bối cảnh

- Module `pay` hiện là code copy từ project bán hàng, **không biên dịch được**: còn dùng `Order`, `OrderDto`, `orderRepository`, `orderUpdateService`, `ApiRes`, `account`, `AppException` (thư viện `com.dev.dungcony`, không có trong project này). Xem `PayOsImpl.java:43-69,140-192`, `PaymentController.java:31-83`, `PaymentException.java:3`.
- Phần dùng lại được: cách gọi SDK payOS (`payOS()` `PayOsImpl.java:212`, `verifyWebhook` `:194`, `isSuccessfulWebhook` `:202`, `buildDescription` `:258`) và `PayOsProperties.java`.
- `users.plan` đã có (`migration_v2/V1__auth_user_role_permission.sql:16,25`), entity `User.java:62-64`, enum `UserPlan.java`. Chưa có hạn dùng, chưa có bảng ghi nhận thanh toán.
- `plan` nằm trong JWT access token (`JwtService.java:69-73`); khi refresh token, `plan` được đọc lại từ DB (`AuthServiceImpl.java:122,136`).
- Yêu cầu: API nâng cấp premium, lịch sử thanh toán cho user và admin; thiết kế để sau này thanh toán cho thứ khác không phải sửa lõi `pay`.
- **Phụ thuộc**: làm sau `plan-remove-enum-converter.md` (enum lưu viết hoa bằng `@Enumerated(EnumType.STRING)`, không còn `LowercaseEnumConverter`, `PermissionName` dùng `.name()`).

## Quyết định đã chốt

| # | Nội dung |
|---|---|
| 1 | Một bảng `payments` dùng chung, phân loại bằng `type` + `reference_id`. Không tạo bảng `premium_orders` riêng. |
| 2 | **Hướng phụ thuộc một chiều `user → pay`.** Nâng cấp là hành động của user nên endpoint nằm ở `user`: `user` kiểm tra điều kiện, tính giá rồi gọi `PaymentService.createPayment(...)`. `pay` không biết nghiệp vụ premium. |
| 3 | Khi đã trả tiền, `pay` phát `PaymentPaidEvent`; `user` nghe bằng `@EventListener` (đồng bộ, cùng transaction với webhook) rồi nâng cấp. Không dùng `@TransactionalEventListener(AFTER_COMMIT)`. Bỏ interface `PaymentFulfillment` của bản plan trước. |
| 4 | Webhook là nguồn chân lý. Endpoint return/cancel chỉ trả trang đích, không nâng cấp. Client polling `GET /payments/{order_code}`. |
| 5 | Giá do server tính (module `user`), client chỉ gửi mã gói. |
| 6 | Premium có hạn, tính theo tháng; 3 gói `PREMIUM_1M`, `PREMIUM_6M`, `PREMIUM_12M`. |
| 7 | Đang premium còn hạn thì **không cho mua thêm**, trả lỗi `PREMIUM_ALREADY_ACTIVE`. `upgradeToPremium` luôn tính hạn từ thời điểm nâng cấp: `plan_expires_at = now + số tháng`. |
| 8 | Sau khi `paid`, client tự refresh token. Client mở link bằng WebView hay trình duyệt ngoài do FE quyết định. |
| 9 | Job `@Scheduled` mỗi ngày hạ user premium hết hạn về `FREE`. |
| 10 | Giá 3 gói trong `.env`: `PREMIUM_PRICE_1M`, `PREMIUM_PRICE_6M`, `PREMIUM_PRICE_12M` (VND, số nguyên), map vào `PremiumPlanProperties`. Không có giá mặc định: thiếu biến thì app báo lỗi khi khởi động (`@Positive`). |
| 11 | Mỗi user chỉ một đơn `PENDING` cho mỗi `type`. Hạn đơn lấy từ `PAYOS_EXPIRED_MINUTES` (đã có, `PayOsProperties.expiredMinutes`), lưu ở `payments.expires_at` và gửi làm `expiredAt` cho payOS. |
| 12 | ~~Chống trùng đơn: `@Idempotent` ở endpoint tạo đơn + unique index một đơn `PENDING` mỗi user mỗi loại + khoá dòng user khi tạo đơn. Còn đơn `PENDING` chưa hết hạn thì **trả lại link cũ, kể cả khi user chọn gói khác**~~ *(⚠️ **ĐÃ BỊ THAY THẾ** bởi plan `plan-payment-cancel-and-recreate-link.md`: Khi tạo đơn mà còn đơn `PENDING`, hệ thống luôn huỷ link cũ trên payOS, chuyển đơn cũ thành `CANCELLED`, và tạo link mới. Hỗ trợ thêm `DELETE /payments/{order_code}`).* |
| 13 | Đơn hết hạn thì huỷ link payOS (`paymentRequests().cancel`) rồi mới ghi `EXPIRED`; payOS báo đã `PAID` thì ghi `PAID` và phát event. Huỷ lỗi thì giữ `PENDING`, lần chạy sau thử lại. |
| 14 | **URL return/cancel bắt buộc lấy từ `.env`** (`PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`), thiếu thì app báo lỗi khi khởi động. Xoá phần tự ghép URL trong `PayOsProperties`. |
| 15 | Webhook: chữ ký sai trả 400; không tìm thấy đơn / sai `amount` / đơn đã xử lý thì log và trả 200, không đổi dữ liệu. Nâng cấp lỗi thì rollback, trả 5xx để payOS gửi lại. |
| 16 | Có viết test: unit test Mockito (rule 2.13) **và integration test Testcontainers** cho controller, webhook, repository. |
| 17 | **Không hoàn tiền, không cho huỷ gói.** Đã trả thì dùng tới hết hạn. |
| 18 | **Job dọn đơn quá hạn chạy định kỳ** (`PaymentExpiryJob`, mỗi 5 phút): tìm đơn `PENDING` có `expires_at < now()`, xử lý từng đơn theo #13. Tạo đơn / hỏi trạng thái vẫn kiểm tra lại hạn để user không phải đợi job. |
| 19 | **Có lịch sử thanh toán**: user xem đơn của chính mình; admin xem toàn hệ thống (chỉ đọc) với quyền mới `PAYMENT_READ`. |

## 1. Các file sẽ thay đổi

Đường dẫn gốc `src/main/java/com/datn/financeapp/`.

**Code — module `pay`**

| File | Thay đổi |
|---|---|
| `module/pay/entity/Payment.java` | **Mới.** Entity bảng `payments`, extends `AssignedIdEntity`, enum dùng `@Enumerated(EnumType.STRING)`. |
| `module/pay/enums/PaymentType.java` | **Mới.** `PREMIUM_UPGRADE`. |
| `module/pay/enums/PaymentStatus.java` | **Mới.** `PENDING, PAID, CANCELLED, EXPIRED`. |
| `module/pay/repository/PaymentRepository.java` | **Mới.** extends `JpaSpecificationExecutor<Payment>` (lọc lịch sử). Query: `findByOrderCodeAndUserId`, `findByOrderCode`, `findPendingByUserIdAndType`, `findByOrderCodeForUpdate` (`@Lock(PESSIMISTIC_WRITE)`), `findOverdueOrderCodes(now, limit)`, `nextOrderCode()` (native `nextval`). |
| `module/pay/helper/PayOsClientHelper.java` | **Mới.** Bọc SDK payOS: `createLink`, `getLink`, `cancelLink`, `verifyWebhook`; chuyển phần khởi tạo client từ `PayOsImpl.java:212-233` sang đây. Lý do: integration test thay bean này bằng `@MockitoBean` (theo mẫu `AuthGoogleLoginIntegrationTest.java:110`) thay vì gọi payOS thật. |
| `module/pay/events/publisher/PaymentPaidEvent.java` | **Mới.** `record PaymentPaidEvent(UUID userId, PaymentType type, String referenceId, long orderCode)`. Theo mẫu `auth/events/publisher/VerifyEmailEvent`. |
| `module/pay/dtos/req/PaymentCreateReq.java` | **Mới.** DTO nội bộ (không nhận từ HTTP): `userId`, `type`, `referenceId`, `amount`. |
| `module/pay/dtos/res/PaymentRes.java` | **Đổi tên** (`git mv`) → `PaymentCreateRes.java` (`orderCode`, `referenceId`, `amount`, `status`, `checkoutUrl`, `expiresAt`). |
| `module/pay/dtos/res/PaymentStatusRes.java` | **Mới.** `orderCode`, `type`, `referenceId`, `amount`, `status`, `paidAt`, `expiresAt`, `createdAt`. Dùng cho cả trạng thái và từng dòng lịch sử của user. |
| `module/pay/dtos/res/PaymentListRes.java` | **Mới.** `items: List<PaymentStatusRes>`, `meta: PageMeta` (theo mẫu `NotificationListResponse.java`). |
| `module/pay/dtos/res/PaymentAdminRes.java` | **Mới.** Như `PaymentStatusRes` + `userId`, `payosLinkId`, `updatedAt`. |
| `module/pay/dtos/res/PaymentAdminListRes.java` | **Mới.** `items: List<PaymentAdminRes>`, `meta: PageMeta`. |
| `module/pay/mapper/PaymentMapper.java` | **Mới.** MapStruct, `unmappedTargetPolicy = ERROR`. |
| `module/pay/services/PayOsService.java` | **Đổi tên** (`git mv`) → `PaymentService.java` (rule 2.14). Public API: `createPayment`, `getStatus`, `listMine`, `processWebhook`, `findOverdueOrderCodes`, `expireIfOverdue`. |
| `module/pay/services/impl/PayOsImpl.java` | **Đổi tên** (`git mv`) → `PaymentServiceImpl.java`. Viết lại phần dùng `Order`, gọi SDK qua `PayOsClientHelper`. |
| `module/pay/services/PaymentAdminService.java` + `services/impl/PaymentAdminServiceImpl.java` | **Mới.** `list(filter, pageable)`, `detail(orderCode)`. Theo mẫu `report/service/ReportAdminService.java`. |
| `module/pay/controllers/PaymentController.java` | **Viết lại**: `GET /payments`, `GET /payments/{order_code}`, webhook, return/cancel; dùng `ApiResponse` + `SecurityContextUtil.currentUserId()`, bỏ tiền tố `/v1`. Không còn `POST /payments`. |
| `module/pay/controllers/PaymentAdminController.java` | **Mới.** `/payments/admin` theo mẫu `BudgetAdminController.java:32` (`/budgets/admin`), `@PreAuthorize` quyền `PAYMENT_READ`. |
| `module/pay/scheduler/PaymentExpiryJob.java` | **Mới.** `@Scheduled(fixedDelay = 5 phút)`, gọi `findOverdueOrderCodes` rồi `expireIfOverdue` từng đơn. |
| `module/pay/config/PayOsProperties.java` | ⚠️ **Sửa**: `@Validated`, `returnUrl`/`cancelUrl` thành `@NotBlank`; xoá `publicAppBaseUrl`, `RETURN_PATH`, `CANCEL_PATH`, `DEFAULT_LOCAL_*`, `resolvedUrl()`, `stripTrailingSlashes()`, nhánh `RAILWAY_PUBLIC_DOMAIN`; `resolvedReturnUrl()`/`resolvedCancelUrl()` chỉ còn `trim()`. |
| `module/pay/exceptions/PaymentException.java` | **Sửa**: extends `BusinessException` + `ErrorCode` (rule 2.12), bỏ `AppException`. |
| `module/pay/exceptions/PaymentInvalidException.java`, `PaymentUserIsIncorrectException.java` | Chưa dùng ở đâu. Khi viết lại nếu vẫn thừa thì **báo bạn trước khi xoá**. |
| `module/pay/exceptions/PaymentNotFoundException.java` | **Mới.** `PAYMENT_ORDER_NOT_FOUND`. |
| `common/exception/ErrorCode.java` | **Thêm** `PAYMENT_ORDER_NOT_FOUND`, `PAYMENT_PROVIDER_ERROR`, `PAYMENT_NOT_CONFIGURED`, `PAYMENT_WEBHOOK_INVALID`, `PREMIUM_ALREADY_ACTIVE`. |
| `common/security/SecurityConfig.java:61-70` | **Thêm** `permitAll` cho `/public/payments/payos/**`. |

**Code — module `user`**

| File | Thay đổi |
|---|---|
| `module/user/controller/UserController.java` | **Thêm** `POST /users/me/premium-upgrades` + `@Idempotent` (như `UserController.java:45`). |
| `module/user/dto/request/PremiumUpgradeReq.java` | **Mới.** `@NotNull PremiumPlan planCode`. |
| `module/user/enums/PremiumPlan.java` | **Mới.** `PREMIUM_1M(1)`, `PREMIUM_6M(6)`, `PREMIUM_12M(12)` (số tháng). |
| `module/user/enums/PermissionName.java` | **Thêm** `PAYMENT_READ` và `Resource.PAYMENT`. |
| `module/user/config/PremiumPlanProperties.java` | **Mới.** `@ConfigurationProperties(prefix = "premium")`, `@Validated`, giá 3 gói `@Positive`. |
| `module/user/service/UserService.java` | **Thêm** `createPremiumUpgrade(UUID userId, PremiumPlan plan)`, `upgradeToPremium(UUID userId, int months)`, `downgradeExpiredPremium()`. |
| `module/user/service/impl/UserServiceImpl.java` | Cài 3 hàm trên. |
| `module/user/repository/UserRepository.java` | **Thêm** `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`), query hạ plan hết hạn. |
| `module/user/entity/User.java` | **Thêm** `planExpiresAt` (`Instant`). |
| `module/user/dto/response/UserRes.java` + `mapper/UserMapper.java` | **Thêm** `planExpiresAt`. ⚠️ thêm trường response. |
| `module/user/event/Listener/PremiumPaymentListener.java` | **Mới.** `@EventListener` nghe `PaymentPaidEvent`, lọc `PREMIUM_UPGRADE`, gọi `upgradeToPremium`. Đặt cạnh `VerifyEmailListener.java`. |
| `module/user/scheduler/PremiumExpiryJob.java` | **Mới.** Theo mẫu `budget/scheduler/BudgetRenewalJob.java:24`, gọi `downgradeExpiredPremium()`. |

**CSDL**

| File | Thay đổi |
|---|---|
| `src/main/resources/db/migration_v2/V16__payments_and_premium.sql` | **Mới.** Bảng `payments`, sequence `payments_order_code_seq`, trigger `updated_at`, cột `users.plan_expires_at`, seed quyền `PAYMENT_READ` + gán cho `ROLE_ADMIN`. Số version sau `V15__create_export_jobs.sql`. |

**Cấu hình**

| File | Thay đổi |
|---|---|
| `application.yml:39-48`, `application-dev.yml:74-83` | Bỏ `public-app-base-url`; thêm khối `premium.prices.*` đọc `${PREMIUM_PRICE_*}` (không mặc định). |
| `.env.example` | Thêm `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`, `PREMIUM_PRICE_1M`, `PREMIUM_PRICE_6M`, `PREMIUM_PRICE_12M` (để trống giá trị). |
| `src/test/resources/application-test.yml` | Thêm giá trị giả cho `payos.return-url`, `payos.cancel-url`, `premium.prices.*` để context test khởi động được (chưa xác minh khối hiện có của file, sẽ đọc trước khi sửa). |

`pom.xml`, `App.java` đã sửa sẵn trong working tree (payOS 2.0.1, `@ConfigurationPropertiesScan`, `@EnableScheduling`), không đổi thêm.

**Test — unit** (dưới `src/test/java/com/datn/financeapp/unitTest/`)

| File | Nội dung |
|---|---|
| `pay/services/impl/PaymentServiceImplTest.java` | **Mới.** Tạo đơn, đơn pending cũ, đơn quá hạn, webhook, `expireIfOverdue`. |
| `pay/services/impl/PaymentAdminServiceImplTest.java` | **Mới.** Lọc, không tìm thấy. |
| `pay/scheduler/PaymentExpiryJobTest.java` | **Mới.** Một đơn lỗi không chặn các đơn sau. |
| `user/service/impl/UserServiceImplPremiumTest.java` | **Mới.** Theo mẫu tên `UserServiceImplGetNamesTest.java`. |
| `user/event/PremiumPaymentListenerTest.java` | **Mới.** |

**Test — integration** (Testcontainers PostgreSQL, `@MockitoBean PayOsClientHelper`, JWT qua `TestAuthSupport.java`)

| File | Nội dung |
|---|---|
| `integrationTest/user/controller/UserPremiumUpgradeIntegrationTest.java` | **Mới.** Luồng tạo đơn qua HTTP. |
| `integrationTest/pay/controller/PaymentWebhookIntegrationTest.java` | **Mới.** Webhook → `PAID` → user thành `PREMIUM`. |
| `integrationTest/pay/controller/PaymentHistoryIntegrationTest.java` | **Mới.** Lịch sử user + admin, phân quyền. |
| `integrationTest/pay/repository/PaymentRepositoryTest.java` | **Mới.** Unique index pending, query quá hạn, sequence (rule 2.13: custom query). |

**Tài liệu**: `docs/api/user.md`, `docs/design/user/api.md` — thêm `POST /users/me/premium-upgrades`, trường `plan_expires_at`. Chưa có tài liệu cho `pay` → thêm `docs/api/payment.md` (**mới**) gồm cả lịch sử và admin.

**Không đổi**: `JwtService.java`, `TokenServiceImpl.java`, `UserAuthCacheHelper.java`.

## 2. Lý do thay đổi

- `pay` không biên dịch được vì phụ thuộc class của project khác (`PayOsImpl.java:46,145,191`, `PaymentController.java:40,44`).
- Chưa có nơi ghi nhận "ai đã trả, cho gì, đã trả chưa" nên không thể làm webhook idempotent, cho client hỏi trạng thái hay xem lịch sử.
- Rule 2.2: không được có vòng phụ thuộc giữa module. Bản plan trước để `pay` tự kiểm tra premium và khoá dòng user → `pay → user`, trong khi `user` cài interface của `pay` → `user → pay`, thành vòng. Bản này chỉ còn `user → pay` (gọi `PaymentService`, nghe `PaymentPaidEvent`).
- Rule 2.9: `GET /payments/{order_code}` và `GET /payments` chỉ trả đơn của `userId` hiện tại.
- Rule 2.4/2.8: dùng `ApiResponse`, phân trang `items` + `meta`, tên resource số nhiều `kebab-case`, query param `snake_case`, không lặp `/v1` (context-path ở `application.yml:3`).
- `PayOsProperties.java` đang cứng `RETURN_PATH = "/v1/api/public/payment/payos/return"`: có `/v1/api` và dùng số ít `payment`, không khớp endpoint mới → khi `.env` trống, payOS chuyển user về link 404.
- **Bỏ bước xoá cache auth** của bản plan trước: `UserAuthCacheHelper` chỉ cache level + authorities (`UserAuthCacheHelper.java:54,86`), không có `plan`; refresh token đọc `plan` từ DB. Xoá cache không có tác dụng gì.
- **Webhook không ném lỗi khi sai dữ liệu**: trả lỗi thì payOS coi là gửi hỏng và gửi lại (chưa xác minh trong tài liệu payOS); request thử khi đăng ký URL webhook có `orderCode` không tồn tại, trả lỗi thì không đăng ký được.
- **Cần job dọn đơn**: nếu chỉ kiểm tra hạn khi user gọi lại, đơn user bỏ dở sẽ nằm `PENDING` mãi, sai lịch sử và chặn unique index.
- **Tách `PayOsClientHelper`**: SDK payOS được khởi tạo bên trong `PayOsImpl` (`:212`), integration test không thay được → phải gọi payOS thật. Bọc thành bean riêng thì test mock được.

## 3. Hướng giải quyết / code xem trước

**Bảng `payments` (⚠️ đổi schema, `V16` trong `migration_v2`)**

```sql
CREATE SEQUENCE payments_order_code_seq START 100000;

CREATE TABLE payments (
    id            UUID         PRIMARY KEY,
    order_code    BIGINT       NOT NULL,
    user_id       UUID         NOT NULL,
    type          VARCHAR(30)  NOT NULL,
    reference_id  VARCHAR(100),
    amount        BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payos_link_id VARCHAR(100),
    expires_at    TIMESTAMPTZ  NOT NULL,
    paid_at       TIMESTAMPTZ,
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_payments_order_code UNIQUE (order_code),
    CONSTRAINT fk_payments_user  FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_type   CHECK (type IN ('PREMIUM_UPGRADE')),
    CONSTRAINT ck_payments_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED'))
);

CREATE UNIQUE INDEX uq_payments_pending_per_user_type
    ON payments (user_id, type) WHERE status = 'PENDING';
CREATE INDEX idx_payments_user    ON payments (user_id, created_at DESC);
CREATE INDEX idx_payments_overdue ON payments (expires_at) WHERE status = 'PENDING';

CREATE TRIGGER trg_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

ALTER TABLE users ADD COLUMN plan_expires_at TIMESTAMPTZ;

INSERT INTO permissions (name, description)
VALUES ('PAYMENT_READ', 'Xem lịch sử thanh toán toàn hệ thống')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'ROLE_ADMIN' AND p.name = 'PAYMENT_READ'
ON CONFLICT (role_id, permission_id) DO NOTHING;
```

- `updated_at`, `is_deleted` có vì `AssignedIdEntity.java` map sẵn. `V10__update_at.sql` chỉ gắn trigger cho bảng có lúc đó → V16 tự gắn (như `V14__create_savings_goals.sql:59`).
- FK `user_id → users` theo convention các bảng khác (`V15__create_export_jobs.sql:15`).
- `order_code` lấy từ sequence → không trùng, `buildDescription` (`PayOsImpl.java:258`) giữ mô tả ≤ 9 ký tự.
- Seed quyền theo mẫu `V1:157,185-190`. Test `seededPermissions_CoverAllPermissionNames` của plan bỏ converter sẽ bắt nếu quên seed.
- Thêm loại thanh toán mới sau này phải sửa `ck_payments_type` bằng migration mới.

**Luồng tạo đơn (`user` gọi `pay`)**

```java
// UserServiceImpl — @Transactional
public PaymentCreateRes createPremiumUpgrade(UUID userId, PremiumPlan plan) {
    User user = userRepository.findByIdForUpdate(userId).orElseThrow(UserNotFoundException::new);
    if (isPremiumActive(user)) {
        throw new BusinessException(ErrorCode.PREMIUM_ALREADY_ACTIVE);
    }
    long amount = premiumPlanProperties.priceOf(plan);
    return paymentService.createPayment(
            new PaymentCreateReq(userId, PaymentType.PREMIUM_UPGRADE, plan.name(), amount));
}
```

`PaymentServiceImpl.createPayment` (cùng transaction, propagation `REQUIRED`):

1. Tìm đơn `PENDING` cùng `userId` + `type`.
2. Đã quá `expires_at` (job chưa kịp chạy) → xử lý như `expireIfOverdue`; huỷ lỗi thì ném `PAYMENT_PROVIDER_ERROR`.
3. Còn hạn → trả lại link cũ, không so `referenceId`/`amount` của yêu cầu mới (#12).
4. Lấy `order_code` từ sequence, lưu `PENDING`, gọi `PayOsClientHelper.createLink`, lưu `payos_link_id`, trả `checkoutUrl`.

**Webhook**

```java
// PaymentServiceImpl — @Transactional
public void processWebhook(Webhook webhook) {
    WebhookData data = payOsClientHelper.verifyWebhook(webhook);   // sai chữ ký → PAYMENT_WEBHOOK_INVALID (400)
    if (!isSuccessfulWebhook(webhook, data)) return;
    paymentRepository.findByOrderCodeForUpdate(data.getOrderCode())
            .ifPresentOrElse(
                    payment -> markPaid(payment, data.getAmount()),
                    () -> log.info("payOS webhook ignored: orderCode={} not found", data.getOrderCode()));
}

private void markPaid(Payment payment, Long paidAmount) {
    if (payment.getStatus() != PaymentStatus.PENDING) return;   // payOS gửi lại → bỏ qua
    if (!Objects.equals(payment.getAmount(), paidAmount)) {
        log.warn("payOS amount mismatch: orderCode={}", payment.getOrderCode());
        return;
    }
    payment.setStatus(PaymentStatus.PAID);
    payment.setPaidAt(Instant.now());
    eventPublisher.publishEvent(new PaymentPaidEvent(
            payment.getUserId(), payment.getType(), payment.getReferenceId(), payment.getOrderCode()));
}
```

`markPaid` dùng chung cho webhook, `getStatus`, `expireIfOverdue` (khi payOS báo đã `PAID`).

**Job dọn đơn quá hạn**

```java
// PaymentExpiryJob
@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
public void expireOverduePayments() {
    for (Long orderCode : paymentService.findOverdueOrderCodes(Instant.now(), BATCH_SIZE)) {
        try {
            paymentService.expireIfOverdue(orderCode);
        } catch (Exception ex) {
            log.error("Expire payment failed: orderCode={}", orderCode, ex);
        }
    }
}
```

```java
// PaymentServiceImpl — @Transactional, mỗi đơn một transaction (gọi qua proxy từ job → không self-invocation, rule 2.5)
public void expireIfOverdue(long orderCode) {
    Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode).orElse(null);
    if (payment == null || payment.getStatus() != PaymentStatus.PENDING
            || payment.getExpiresAt().isAfter(Instant.now())) return;   // đã được webhook xử lý trong lúc chờ khoá
    PaymentLink link = payOsClientHelper.cancelLink(orderCode);         // lỗi → ném, đơn giữ PENDING, lần sau thử lại
    if (link.getStatus() == PaymentLinkStatus.PAID) {
        markPaid(payment, link.getAmount());
        return;
    }
    payment.setStatus(PaymentStatus.EXPIRED);
}
```

**Listener phía `user`**

```java
@EventListener
public void onPaymentPaid(PaymentPaidEvent event) {
    if (event.type() != PaymentType.PREMIUM_UPGRADE) return;
    PremiumPlan plan = PremiumPlan.valueOf(event.referenceId());
    userService.upgradeToPremium(event.userId(), plan.getMonths());
}
```

**`getStatus(userId, orderCode)`**: `findByOrderCodeAndUserId` (không thấy → `PAYMENT_ORDER_NOT_FOUND`). Còn `PENDING` mà quá hạn → `expireIfOverdue`. Còn `PENDING` chưa quá hạn → hỏi lại payOS (`getLink`), `PAID` thì `markPaid`.

**Lịch sử**

- User `listMine(userId, status, type, pageable)`: luôn ép điều kiện `user_id = userId` (rule 2.9), sắp xếp `created_at DESC`.
- Admin `list(userId?, status?, type?, fromDate?, toDate?, pageable)` dùng `Specification`; `detail(orderCode)` dùng `findByOrderCode`.

```java
@PreAuthorize("hasAuthority(T(com.datn.financeapp.module.user.enums.PermissionName).PAYMENT_READ.name())")
```

**`PayOsProperties` sau khi sửa**

```java
@Validated
@ConfigurationProperties(prefix = "payos")
public record PayOsProperties(
        boolean enabled,
        String clientId,
        String apiKey,
        String checksumKey,
        String baseUrl,
        @NotBlank String returnUrl,
        @NotBlank String cancelUrl,
        long expiredMinutes
) { ... }
```

**API contract (⚠️ endpoint mới, ảnh hưởng app Flutter)**

| Method | Path | Auth | Ghi chú |
|---|---|---|---|
| POST | `/users/me/premium-upgrades` | JWT | body `{ "plan_code": "PREMIUM_6M" }` → `PaymentCreateRes` (`order_code`, `reference_id`, `amount`, `status`, `checkout_url`, `expires_at`). Đang có đơn chờ thì trả đơn đó, `reference_id` có thể khác `plan_code` vừa gửi. `@Idempotent`. |
| GET | `/payments` | JWT | Lịch sử của chính user. `?status=&type=&page=1&page_size=20` → `items` + `meta`. |
| GET | `/payments/{order_code}` | JWT | Chỉ đơn của chính user → `PaymentStatusRes`. |
| GET | `/payments/admin` | `PAYMENT_READ` | `?user_id=&status=&type=&from_date=&to_date=&page=&page_size=` → `items` + `meta`. |
| GET | `/payments/admin/{order_code}` | `PAYMENT_READ` | Chi tiết bất kỳ đơn nào → `PaymentAdminRes`. |
| POST | `/public/payments/payos/webhook` | public | verify chữ ký payOS. |
| GET | `/public/payments/payos/return`, `/cancel` | public | Trang HTML đơn giản "đóng cửa sổ", không đổi dữ liệu. `.env` có thể trỏ thẳng về deep link app thay vì 2 endpoint này. |

`GET /users/me` thêm trường `plan_expires_at`. Đường `/payments/admin` là chuỗi cố định nên Spring ưu tiên hơn `/payments/{order_code}`.

Client: gọi `POST /users/me/premium-upgrades` → mở `checkout_url` → polling `GET /payments/{order_code}` mỗi 2–3 giây → khi `PAID` thì refresh token.

**Đỏ-xanh**: viết `PaymentServiceImplTest` trước, đỏ vì:
- webhook gọi hai lần thì event phát hai lần (khẳng định `verify(eventPublisher, times(1))`);
- webhook sai `amount` thì `status` phải vẫn `PENDING` và không phát event;
- `getStatus` đơn của user khác phải ném `PAYMENT_ORDER_NOT_FOUND`;
- `expireIfOverdue` khi huỷ link lỗi thì `status` phải vẫn `PENDING`.

## 4. Danh sách công việc chi tiết

1. Migration `V16` (bảng `payments`, sequence, index, trigger, `users.plan_expires_at`, quyền `PAYMENT_READ`).
2. `git mv` 3 file `pay`; entity, enum, repository, mapper, DTO, event, `PayOsClientHelper`.
3. `ErrorCode` + exception `pay` chuyển sang `BusinessException`.
4. Sửa `PayOsProperties` + `application*.yml` + `application-test.yml` + `.env.example`.
5. Viết lại `PaymentServiceImpl` (tạo link, webhook, trạng thái, lịch sử, `expireIfOverdue`).
6. `PaymentAdminService`/`Impl`, `PaymentAdminController`.
7. `PaymentExpiryJob`.
8. Viết lại `PaymentController`; thêm `permitAll` ở `SecurityConfig`.
9. Module `user`: `PremiumPlan`, `PremiumPlanProperties`, `PAYMENT_READ`, `planExpiresAt`, `createPremiumUpgrade`, `upgradeToPremium`, `downgradeExpiredPremium`, endpoint, listener, `PremiumExpiryJob`.
10. Unit test:
    - `PaymentServiceImplTest`: tạo đơn mới; còn đơn pending trả cùng link (kể cả yêu cầu khác gói, không gọi payOS tạo link mới); đơn quá hạn → huỷ link → `EXPIRED` → tạo được đơn mới; huỷ lỗi thì không tạo đơn mới; payOS báo `PAID` lúc huỷ thì ghi `PAID` + phát event; webhook idempotent; sai amount; không thấy đơn không ném lỗi; đơn user khác; `expireIfOverdue` bỏ qua đơn đã `PAID` / chưa tới hạn; `listMine` luôn lọc theo `userId`.
    - `PaymentAdminServiceImplTest`: lọc theo điều kiện; `detail` không thấy → `PAYMENT_ORDER_NOT_FOUND`.
    - `PaymentExpiryJobTest`: đơn giữa danh sách ném lỗi thì các đơn sau vẫn được xử lý.
    - `UserServiceImplPremiumTest`: đang premium còn hạn → `PREMIUM_ALREADY_ACTIVE`; giá lấy đúng theo gói; FREE → PREMIUM, hạn = now + tháng; hết hạn mua lại tính từ now; hạ plan chỉ user quá hạn.
    - `PremiumPaymentListenerTest`: đúng loại thì gọi `upgradeToPremium` đúng số tháng; khác loại thì bỏ qua.
11. Integration test:
    - `UserPremiumUpgradeIntegrationTest`: không JWT → 401; tạo đơn → 200 + `checkout_url` + dòng `PENDING` trong DB; gọi lại khác gói → cùng `order_code`; đang premium còn hạn → lỗi `PREMIUM_ALREADY_ACTIVE`; `plan_code` sai → 400.
    - `PaymentWebhookIntegrationTest`: webhook hợp lệ → `PAID`, user `PREMIUM` có `plan_expires_at`; gửi lại → hạn không đổi; chữ ký sai → 400; không thấy đơn → 200; sai amount → 200 và vẫn `PENDING`; `GET /payments/{order_code}` của user khác → 404.
    - `PaymentHistoryIntegrationTest`: `GET /payments` chỉ thấy đơn của mình + đúng `meta`; `GET /payments/admin` không có `PAYMENT_READ` → 403, có → thấy mọi đơn, lọc `user_id`/`status` đúng.
    - `PaymentRepositoryTest`: tạo 2 đơn `PENDING` cùng user cùng loại → vi phạm unique index; `findOverdueOrderCodes` chỉ trả đơn `PENDING` quá hạn; `nextOrderCode` tăng dần.
12. Cập nhật tài liệu.
13. Biên dịch, `git diff` đối chiếu mục 1, báo cáo.

## Ngoài phạm vi

- ⚠️ **`application-dev.yml` vẫn đang được đẩy lên git.** File có trong `.gitignore:10` nhưng đã được commit từ trước nên git vẫn theo dõi (`git ls-files` còn thấy). `.gitignore` chỉ có tác dụng với file chưa được theo dõi. Hệ quả:
    - Bản đã commit (`HEAD`, commit `9702bc3`) có sẵn `api-key`/`api-secret` Cloudinary và đã có trên remote `team/check-db`.
    - Khoá payOS hiện chỉ nằm trong working tree, nhưng commit tiếp theo sẽ đẩy lên luôn.
    - Đề xuất (bạn tự làm hoặc plan riêng): `git rm --cached src/main/resources/application-dev.yml`, chuyển khoá sang `.env`, **đổi khoá Cloudinary** vì đã nằm trong lịch sử git.
- Thao tác admin có ghi (đánh dấu đã trả thủ công, huỷ đơn hộ) — plan này admin chỉ đọc.

## Kiểm tra

- Hỏi bạn trước khi chạy test; không chạy full test.
- Lệnh (khi đã có lớp test; integration test cần Docker):

```powershell
mvn test -Dtest=PaymentServiceImplTest,PaymentAdminServiceImplTest,PaymentExpiryJobTest,UserServiceImplPremiumTest,PremiumPaymentListenerTest
```

```powershell
mvn test -Dtest=PaymentRepositoryTest,UserPremiumUpgradeIntegrationTest,PaymentWebhookIntegrationTest,PaymentHistoryIntegrationTest
```

- `grep` không còn tham chiếu rác:

```powershell
rg -n "Order\b|OrderDto|ApiRes\b|AppException|com\.dev\.dungcony|publicAppBaseUrl|RAILWAY_PUBLIC_DOMAIN|PayOsService|PayOsImpl" src/main/java
```

- Bạn tự chạy full test nếu cần kết quả tổng.

## Câu hỏi còn mở

Không còn.

## Ghi chú khi thực hiện (lệch nhỏ so với plan)

- Không tạo `PAYMENT_ORDER_NOT_FOUND`: `PaymentNotFoundException` dùng chung mã `NOT_FOUND` theo quy tắc bất biến số 7 (`ErrorCode.java` phần `NOT_FOUND`), test và `docs/api/payment.md` ghi `NOT_FOUND`.
- `expireIfOverdue` / đơn quá hạn: gọi `getLink` trước, `PAID` thì ghi `PAID`, đã `CANCELLED`/`EXPIRED` ở payOS thì không gọi huỷ lại, còn lại mới `cancelLink`. Lý do: chưa xác minh payOS có từ chối huỷ link đã trả hay không.
- Tạo đơn khi đơn chờ quá hạn mà payOS báo đã trả: trả luôn đơn `PAID` (không tạo đơn mới).
- Webhook nhận body dạng `String` rồi parse bằng `ObjectMapper` riêng (camelCase) để không phụ thuộc naming strategy `SNAKE_CASE` của Spring.
- Thêm `module/pay/helper/PaymentSpecs.java` (bộ lọc lịch sử) ngoài danh sách file của mục 1; thư mục `pay` dùng tên số ít (`controller`, `service`, `dto`) theo trạng thái working tree hiện tại.
- `UserRes` thêm `planExpiresAt` kèm constructor cũ 10 tham số để không phải sửa các test đang dựng `UserRes`.
- Seed quyền `PAYMENT_READ` trong `V16` lưu tên enum viết hoa; `PermissionName.fromValue` đọc được cả hai dạng nên chạy được trước và sau `plan-remove-enum-converter.md`.
- Chưa làm: test cho nhánh "loại thanh toán khác `PREMIUM_UPGRADE`" của `PremiumPaymentListener` vì enum `PaymentType` mới có một giá trị.

## Bảng công việc

| # | Công việc | Trạng thái |
|---|---|---|
| 1 | Migration `V16` | Hoàn thành |
| 2 | `git mv` + entity, enum, repository, mapper, DTO, event, `PayOsClientHelper` | Hoàn thành |
| 3 | `ErrorCode` + exception `pay` | Hoàn thành |
| 4 | `PayOsProperties` + cấu hình | Hoàn thành |
| 5 | `PaymentServiceImpl` | Hoàn thành |
| 6 | `PaymentAdminService`, `PaymentAdminController` | Hoàn thành |
| 7 | `PaymentExpiryJob` | Hoàn thành |
| 8 | `PaymentController`, `SecurityConfig` | Hoàn thành |
| 9 | Module `user` (gói, endpoint, listener, job) | Hoàn thành |
| 10 | Unit test | Hoàn thành (đã biên dịch, chưa chạy) |
| 11 | Integration test | Hoàn thành (đã biên dịch, chưa chạy, cần Docker) |
| 12 | Tài liệu | Hoàn thành |
| 13 | Biên dịch, đối chiếu diff, báo cáo | Hoàn thành |
