# Huỷ đơn, luôn tạo link mới, siết validate + test luồng thanh toán, và xử lý thanh toán thiếu tiền

## Bối cảnh

> File này gồm **hai giai đoạn** trong một plan (user chốt gộp chung):
> - **Giai đoạn 1** (các mục 1-4 bên dưới): huỷ đơn, luôn tạo link mới, siết validate và test. Code được ngay.
> - **Giai đoạn 2** (cuối file): thanh toán thiếu tiền, có đổi schema `V19`, bị chặn bởi việc xác minh payOS (G2-A). Làm sau Giai đoạn 1.

- Plan `plan-premium-upgrade-payment.md` (đã code) chốt #12: còn đơn `PENDING` chưa hết hạn thì **trả lại link cũ, kể cả khi user chọn gói khác** (`PaymentServiceImpl.java:128-139`, `reuseOrResolvePending`).
- Hệ quả:
    - User bấm huỷ ngay trên trang payOS thì server không biết: đơn local vẫn `PENDING` tới khi quá `PAYOS_EXPIRED_MINUTES`. Bấm nâng cấp lại nhận đúng `checkout_url` đã chết.
    - User chọn gói khác vẫn nhận link gói cũ, phải đợi hết hạn mới đổi được gói.
    - `PaymentStatus.CANCELLED` có trong enum nhưng không có chỗ nào ghi.
    - Polling `syncPending` (`PaymentServiceImpl.java:166-184`) chỉ xử lý payOS báo `PAID`, bỏ qua `CANCELLED`/`EXPIRED`.
- Yêu cầu của user:
    1. Tạo đơn **luôn tạo link mới và huỷ link cũ**; thêm API huỷ đơn.
    2. Các chỗ dính tới tiền **phải validate thật kỹ và viết test thật kỹ**.
- Hai việc cùng viết lại `createPayment`, `closeUnpaid`, `syncPending`, `markPaid` và cùng sửa `PaymentServiceImplTest` nên gộp một plan (user chốt), thay cho plan `plan-payment-hardening.md` (đã xoá, nội dung nằm ở đây).
- Mục "Lý do" liệt kê R1-R13 là kết quả rà soát code hiện tại; điều chưa đọc tới ghi rõ "chưa xác minh".

## Quyết định đã chốt

| # | Nội dung |
|---|---|
| 1 | Thay quyết định #12 của plan premium: khi tạo đơn mà còn đơn `PENDING`, **luôn huỷ đơn cũ rồi tạo đơn mới + link mới**, kể cả cùng gói. Bấm đúp / gửi lại cùng `Idempotency-Key` vẫn được `@Idempotent` trả đúng response cũ. |
| 2 | Huỷ link cũ trước, tạo link mới sau (không bao giờ có hai link cùng sống). Huỷ lỗi thì ném `PAYMENT_PROVIDER_ERROR`, đơn cũ giữ `PENDING`, không tạo đơn mới. |
| 3 | Trước khi huỷ đơn cũ, hỏi payOS (`getLink`): nếu đã `PAID` thì ghi `PAID`, nâng cấp, **trả đơn đã trả, không tạo đơn mới**. |
| 4 | Thêm `DELETE /payments/{order_code}` cho user huỷ đơn `PENDING` của chính mình. Idempotent: trả trạng thái hiện tại của đơn (`CANCELLED`, hoặc `PAID`/`EXPIRED` nếu đơn đã kết thúc), không ném lỗi. Không ném lỗi khi payOS báo đã `PAID` vì ném trong transaction sẽ rollback việc ghi `PAID`. |
| 5 | Polling `GET /payments/{order_code}`: payOS báo link `CANCELLED` thì ghi `CANCELLED`, `EXPIRED` thì ghi `EXPIRED`. |
| 6 | Đơn bị thay thế do tạo đơn mới và đơn user chủ động huỷ đều ghi `CANCELLED`; đơn quá hạn ghi `EXPIRED` như cũ. |
| 7 | Có viết test (sửa test cũ + thêm test mới), có thêm request Bruno. |
| 8 | **Tiền đã vào thì không được im lặng bỏ qua.** Số tiền chỉ tin vào số payOS xác nhận **đã thu**, không tin số tiền ta yêu cầu. |
| 9 | **Không thêm trạng thái `REVIEW`** (user chốt; Giai đoạn 1 chỉ thêm cột `amount_paid` theo #20). Ca tiền về mà không giao được hàng chỉ để lại log `ERROR` tiền tố `[PAYMENT-RECONCILE]` (kèm `order_code`, `user_id`, số tiền, trạng thái đơn, lý do). |
| 10 | User bị xoá khi đang nâng cấp: đơn vẫn ghi `PAID` (tiền đã vào), **bỏ qua bước nâng cấp**, log `ERROR`, **không ném lỗi** (tránh payOS gửi lại webhook mãi). `referenceId` sai là lỗi lập trình nên vẫn ném để rollback. |
| 11 | Đơn cũ trả muộn lúc user đã premium (hoặc đã có đơn mới): `upgradeToPremium` cộng dồn từ hạn hiện tại nên không ai mất tiền; không huỷ đơn mới. |
| 12 | Test: unit (Mockito) + integration (Testcontainers) + **test đa luồng thật** trên PostgreSQL. Test khẳng định cả trạng thái DB lẫn số lần giao hàng, không chỉ HTTP status. |
| 13 | **Đơn `CANCELLED`/`EXPIRED` mà vẫn nhận tiền là lỗi hệ thống, phải chặn từ gốc**, không xử lý bằng cách thừa nhận `PAID` (user chốt). Chặn bằng: (a) chỉ ghi `CANCELLED`/`EXPIRED` **sau khi** payOS xác nhận link đã chết: sau `cancelLink` đọc trạng thái link trả về, `CANCELLED` mới ghi, `PAID` thì `markPaid`, trạng thái khác thì ném lỗi và giữ `PENDING`; (b) mọi lần đổi trạng thái đều giữ khoá dòng đơn (`findByOrderCodeForUpdate`), webhook dùng cùng khoá nên không chen vào giữa. Nếu vẫn có webhook hợp lệ cho đơn đã đóng: **không đổi trạng thái, không giao hàng**, log `ERROR [PAYMENT-RECONCILE]` như một báo động "bất biến đã bị vỡ". |
| 14 | **Quyết định đóng đơn dựa trên "payOS đã nhận bao nhiêu tiền", không dựa trên từng trạng thái** (`UNDERPAID`/`PROCESSING`/`FAILED`... chưa có ngữ nghĩa xác minh). `received` = số lớn hơn giữa tổng `PaymentLink.getTransactions()[].getAmount()` và `getAmountPaid()` (không bao giờ đánh giá thấp tiền đã nhận; cách này cũng không phụ thuộc việc payOS cộng dồn hay không, hay có gửi webhook cho khoản thiếu hay không). Quy tắc: `received >= amount` → `PAID`; `0 < received < amount` → **không đóng đơn**, giữ `PENDING`, log `ERROR [PAYMENT-RECONCILE]`, ném `PAYMENT_PROVIDER_ERROR` (hành vi tạm của Giai đoạn 1, **Giai đoạn 2 thay thế** bằng luồng `PARTIALLY_PAID`); `received == 0` → đóng được: link `CANCELLED`/`EXPIRED`/`FAILED` thì ghi thẳng, trạng thái khác thì `cancelLink` rồi đọc lại. Link báo `PAID` mà `received < amount` (thiếu số liệu) thì giữ `PENDING` và log ERROR, không đóng. |
| 15 | `order_code` (R9): giữ sequence. Dev và prod dùng **khoá payOS khác nhau** (user xác nhận) nên không trùng mã; không sửa code, không thêm migration cho việc này. Ghi vào `docs/api/payment.md` quy ước "mỗi môi trường một khoá payOS riêng". |
| 16 | Giá tối thiểu mỗi gói là **2000 VND** (user chốt): `PremiumPlanProperties` kiểm tra `@Min(2000)` cho từng giá, `PayOsClientHelper.createLink` từ chối `amount < 2000`; thiếu hoặc nhỏ hơn thì app không khởi động. |
| 17 | Tiêm `java.time.Clock` (bean `Clock.systemUTC()` trong `common/configs`) vào **`PaymentServiceImpl`, `UserServiceImpl` và `PaymentExpiryJob`** thay cho `Instant.now()` (đúng 9 chỗ: `PaymentExpiryJob.java:27` 1 chỗ, `PaymentServiceImpl` 4 chỗ, `UserServiceImpl` 4 chỗ). `PaymentAdminServiceImpl` không có `Instant.now()` nên **không** tiêm. Test dùng `MutableClock` (tua được thời gian) cho cả service lẫn job để fuzz "tua thời gian" khớp với job quá hạn. Hành vi chạy thật không đổi. |
| 18 | **Luôn khoá đơn trước khi đọc trạng thái**: không bao giờ nạp `Payment` vào persistence context bằng truy vấn không khoá rồi mới gọi `findByOrderCodeForUpdate` (Hibernate trả lại object cũ, trạng thái cũ, nên webhook commit xen giữa làm `markPaid` chạy hai lần và `upgradeToPremium` cộng tháng hai lần). Kiểm tra quyền sở hữu bằng truy vấn không nạp entity (`existsByOrderCodeAndUserId`), rồi mới khoá-và-đọc. Sửa cả `getStatus` hiện có. |
| 19 | **Một thứ tự khoá duy nhất: đơn rồi mới tới user** (như webhook). `createPayment` đang đi ngược (user → đơn) nên khoá đơn chờ bằng `NOWAIT` (`jakarta.persistence.lock.timeout = 0`): đơn đang bị webhook/job khoá thì trả `409 REQUEST_IN_PROGRESS` để FE thử lại, không chờ nên không thể deadlock. **Phải đổi lỗi khoá thành 409 ngay trong `createPayment`**: `GlobalExceptionHandler` không có handler cho lỗi khoá (chỉ có `BusinessException`, `ObjectOptimisticLockingFailureException`, `DataIntegrityViolationException`, `Exception`), nên nếu không bắt thì `NOWAIT` thất bại rơi vào `Exception.class` và ra `500`. Bắt `PessimisticLockingFailureException` (Spring dịch `PessimisticLockException` và `LockTimeoutException` của Hibernate về lớp này) quanh đúng lần gọi `findPendingForUpdateNoWait` rồi ném `BusinessException(ErrorCode.REQUEST_IN_PROGRESS)`. Không thêm handler toàn cục để khỏi đổi hành vi của module khác. |
| 20 | **Trả dư (thừa tiền)**: số tiền thu `>=` số đơn thì **vẫn giao hàng** (`PAID`); phần dư **không hoàn, không quy đổi** (user chốt: "nuốt", khi user khiếu nại thì tra thanh toán để xác nhận có chuyển thừa và thừa bao nhiêu). Vì vậy phải **lưu số tiền thực nhận** vào `payments.amount_paid` và hiển thị phần dư (`excess_amount = amount_paid - amount`) để tra cứu: admin thấy trong `GET /payments/admin`, user thấy trong lịch sử của chính mình. Hoàn tự động đã cân nhắc nhưng không làm (chưa tìm thấy API hoàn tiền cho người trả của payOS, **chưa xác minh**; phải dùng dịch vụ chuyển tiền đi và thu thập tài khoản ngân hàng của user). Trả thiếu vẫn không giao hàng, do Giai đoạn 2 xử lý. |
| 21 | **Webhook không tìm thấy đơn**: `orderCode >= FIRST_ORDER_CODE` (100000, khớp `START` của `payments_order_code_seq` ở `V16`) thì log `ERROR [PAYMENT-RECONCILE]` (kèm `orderCode`, `amount`, `paymentLinkId`) vì đây là **link mồ côi** (tạo link thành công nhưng transaction rollback sau đó, VD deadlock) mà tiền đã vào; `orderCode < FIRST_ORDER_CODE` chắc chắn không phải của ta (VD webhook thử khi đăng ký URL) nên chỉ `INFO`. Luôn trả `200` để payOS không gửi lại mãi (quyết định đã có ở plan premium). |
| 22 | **Phòng ngừa link mồ côi** (phần người viết plan bổ sung, bạn có thể gạch bỏ): ngay sau `createLink` thành công, đăng ký `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` để gọi `cancelLink(orderCode)` bù trừ; huỷ lỗi thì log `ERROR [PAYMENT-RECONCILE]`. Nhờ đó link mồ côi bị đóng trước khi user kịp trả, thay vì chỉ phát hiện sau khi mất tiền (#21). |
| 23 | ~~Trang return tự xác nhận với payOS~~ **Đã làm rồi gỡ bỏ theo yêu cầu của user** (trên production webhook đã đủ, trang return public thêm bề mặt tấn công mà ít giá trị). `GET /public/payments/payos/return` trả lại trang HTML tĩnh, không đổi dữ liệu; `handleReturn` và test liên quan đã xoá. Chạy local thì gọi `GET /payments/{order_code}` hoặc dùng ngrok cho webhook. |

## 1. Các file sẽ thay đổi

Đường dẫn gốc `src/main/java/com/datn/financeapp/`.

**Code**

| File | Thay đổi |
|---|---|
| `module/pay/service/PaymentService.java` | Thêm `PaymentStatusRes cancel(UUID userId, long orderCode)`; mô tả quy ước khoá (đơn trước, user sau); sửa javadoc `createPayment` (không còn "trả lại link cũ"). |
| `module/pay/service/impl/PaymentServiceImpl.java:57-67,89-106,128-139,166-217` | Sửa `createPayment` theo #1-#3 + validate đầu vào (R6); gộp `resolveOverdue` thành `closeUnpaid(payment, targetStatus)`; mở rộng `syncPending` (#5); thêm `cancel`; sửa `processWebhook`/`markPaid` (R1-R4, R10: đối chiếu `paymentLinkId`, số tiền đã thu, log đối soát); mọi chỗ dùng `link.getAmount()` đổi sang `getAmountPaid()` (R3); `createPayment` bắt `PessimisticLockingFailureException` đổi thành `BusinessException(REQUEST_IN_PROGRESS)` (#19); `processWebhook` log `ERROR` khi không có đơn và `orderCode >= FIRST_ORDER_CODE` (#21); `createNewPayment` huỷ link bù trừ khi rollback (#22). |
| `module/pay/dto/req/PaymentCreateReq.java` | Kiểm tra trong compact constructor: `userId`, `type`, `referenceId` không null/blank, `amount > 0` (R6). |
| `module/pay/helper/PayOsClientHelper.java` | `createLink` kiểm tra `amount > 0`, `orderCode > 0`, `expiredAt` trong tương lai trước khi gọi SDK (R6). |
| `module/pay/repository/PaymentRepository.java` | Thêm `existsByOrderCodeAndUserId` (R12) và `findPendingForUpdateNoWait(userId, type, status)` với `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))` (R13). |
| `module/pay/entity/Payment.java` | Thêm `amountPaid` (số tiền thực nhận, **chỉ** ghi lúc đơn chuyển `PAID`; đơn `CANCELLED`/`EXPIRED`/`PENDING` luôn `null`). |
| `module/pay/dto/res/PaymentStatusRes.java`, `PaymentAdminRes.java` | Thêm `amountPaid` và `excessAmount` (`amountPaid - amount` khi `> 0`, ngược lại `0`) (#20). |
| `module/pay/mapper/PaymentMapper.java` | Cập nhật ánh xạ (`excessAmount` qua `default method`). |
| `module/pay/controller/PaymentController.java` | Thêm `DELETE /payments/{order_code}`; (trang return giữ nguyên HTML tĩnh, xem #23). |
| `module/pay/config/PayOsProperties.java` | `@Min(1)` cho `expiredMinutes`; bỏ nhánh âm thầm đổi `<= 0` thành 15 (R8). |
| `module/user/config/PremiumPlanProperties.java` | `@Min(2000)` cho từng giá (quyết định #16); đủ giá cho mọi giá trị `PremiumPlan` (R7). |
| `module/user/service/impl/UserServiceImpl.java` | `upgradeToPremium`: kiểm tra `months > 0`. |
| `module/user/event/Listener/PremiumPaymentListener.java` | `referenceId` sai thì ném lỗi **kèm log ERROR có `order_code`** (R10 cũ, hiện chỉ `valueOf` ném `IllegalArgumentException` trần); user không tồn tại thì log ERROR rồi bỏ qua (#10). |
| `common/configs/ClockConfig.java` | **Mới.** Bean `Clock.systemUTC()`. |
| `module/pay/scheduler/PaymentExpiryJob.java:27` | Tiêm `Clock` thay cho `Instant.now()` (#17); logic không đổi. |
| `module/user/service/UserService.java` | Sửa comment `createPremiumUpgrade` ("còn đơn chờ thì trả lại đơn đó" không còn đúng). |

**CSDL**

| File | Thay đổi |
|---|---|
| `src/main/resources/db/migration_v2/V17__payment_amount_paid.sql` | **Mới.** `ALTER TABLE payments ADD COLUMN amount_paid BIGINT CHECK (amount_paid IS NULL OR amount_paid >= 0)`; đơn `PAID` cũ (nếu có) điền `amount_paid = amount`. ⚠️ Số version sau `V16`; **Giai đoạn 2 chuyển sang `V18`**. `CANCELLED` đã nằm sẵn trong `ck_payments_status`. |

**Test** (dưới `src/test/java/com/datn/financeapp/`)

| File | Thay đổi |
|---|---|
| `unitTest/pay/service/impl/PaymentServiceImplTest.java` | **Sửa** `createPayment_PendingNotExpired_ReturnsSameLink` (`:168`) → "còn đơn chờ: huỷ link cũ, tạo link mới". **Thêm** ca huỷ đơn, ma trận webhook, ma trận chuyển trạng thái, biên số tiền/thời gian (mục 3). |
| `unitTest/pay/dto/req/PaymentCreateReqTest.java` | **Mới.** Đầu vào sai. |
| `unitTest/pay/config/PayOsPropertiesTest.java` | **Mới.** Cấu hình thiếu/sai, dùng `ApplicationContextRunner`. |
| `unitTest/user/config/PremiumPlanPropertiesTest.java` | **Mới.** Giá thiếu/âm/0; `priceOf` đủ mọi gói. |
| `unitTest/user/service/impl/UserServiceImplPremiumTest.java` | **Thêm** ca biên hạn dùng, `months` sai. |
| `unitTest/user/event/Listener/PremiumPaymentListenerTest.java` | **Thêm** ca `referenceId` null/rỗng/sai chữ hoa, user không tồn tại. |
| `integrationTest/user/controller/UserPremiumUpgradeIntegrationTest.java` | **Sửa** `upgrade_WithPendingOrder_ReturnsSameOrder` (`:168`) → hai lần gọi ra hai `order_code` khác nhau, đơn cũ `CANCELLED`, chỉ một đơn `PENDING`. |
| `integrationTest/pay/controller/PaymentCancelIntegrationTest.java` | **Mới.** `DELETE /payments/{order_code}` qua HTTP. |
| `integrationTest/pay/controller/PaymentWebhookIntegrationTest.java` | **Thêm** ca sai số tiền, đơn đã đóng, link không khớp, đúng một lần giao hàng. |
| `integrationTest/pay/controller/PaymentConcurrencyIntegrationTest.java` | **Mới.** Đa luồng thật. |
| `integrationTest/pay/controller/PaymentValidationIntegrationTest.java` | **Mới.** Validate đầu vào HTTP. |
| `unitTest/pay/service/impl/PaymentInvariantTest.java` | **Mới.** Bảng đầy đủ chứng minh bất biến #13 ở mức unit (mục 3, nhóm I1). |
| `unitTest/pay/scheduler/PaymentExpiryJobTest.java` | **Sửa** theo constructor mới có `Clock` (dùng `Clock.fixed`). |
| `integrationTest/pay/support/MutableClock.java` | **Mới.** `Clock` tua được, dùng chung cho service và job trong test thời gian/fuzz (#17). |
| `integrationTest/pay/support/FakePayOsClient.java` | **Mới.** payOS giả có trạng thái, thay `PayOsClientHelper` bằng `@TestConfiguration` + `@Primary` cho các test bất biến. |
| `integrationTest/pay/controller/PaymentLockOrderIntegrationTest.java` | **Mới.** Đọc cũ sau khoá và deadlock (nhóm I4). |
| `integrationTest/pay/controller/PaymentRaceIntegrationTest.java` | **Mới.** Race huỷ đơn vs trả tiền (nhóm I2). |
| `integrationTest/pay/controller/PaymentInvariantFuzzIntegrationTest.java` | **Mới.** Chuỗi thao tác ngẫu nhiên, kiểm tra bất biến sau mỗi bước (nhóm I3). |
| `integrationTest/pay/repository/PaymentRepositoryTest.java` | **Thêm** ca vi phạm `CHECK` (amount ≤ 0, status/type lạ). |

**Tài liệu / Bruno**

| File | Thay đổi |
|---|---|
| `docs/api/payment.md` | Sửa mục 3.1 (bỏ "trả lại đúng đơn đó"), thêm mục huỷ đơn, cập nhật bảng endpoint và mục 4 (xử lý tiền về mà không giao được hàng; **trả dư: vẫn giao hàng, không hoàn phần dư, tra được bằng `amount_paid`/`excess_amount`**; quy ước `Idempotency-Key` cho FE: mỗi lần user bấm sinh một key, retry gửi lại đúng key đó, khoá nút khi đang chờ). |
| `docs/api/user.md`, `docs/design/user/api.md` | Sửa mục 3.6 (hai file đang giống hệt nhau). |
| `bruno/15-Payments/User/03-Cancel-Payment.yml` | **Mới.** |
| `.agents/.plan/plan-premium-upgrade-payment.md` | Đánh dấu quyết định #12 đã bị plan này thay thế. |

**Không đổi**: `PayOsClientHelper` ngoài phần validate; `PaymentAdminServiceImpl` (không dùng thời gian).

## 2. Lý do thay đổi

**Huỷ đơn / link mới**

- Link chết bị trả lại: `createPayment` chỉ so `expires_at` với giờ hiện tại (`PaymentServiceImpl.java:128-131`), không hỏi payOS.
- Gói khác vẫn nhận link gói cũ: bản plan trước chốt như vậy vì chưa có cách huỷ đơn; giờ có `cancelLink` nên bỏ được ràng buộc.
- Polling không thấy huỷ: `syncPending` chỉ nhánh `PAID` (`PaymentServiceImpl.java:176-178`).

**Validate tiền (rà soát code hiện tại, xếp theo mức nguy hiểm)**

| # | Vấn đề | Căn cứ | Kịch bản / cách sửa |
|---|---|---|---|
| R1 ⚠️ | Đơn có thể bị ghi `CANCELLED`/`EXPIRED` mà chưa chắc link payOS đã chết, và webhook thu tiền cho đơn đã đóng bị bỏ qua không dấu vết | `resolveOverdue`: gọi `cancelLink` nhưng bỏ qua giá trị trả về rồi ghi `EXPIRED`; `markPaid`: `if (status != PENDING) return;` không phân biệt "đã `PAID` (replay)" với "đã đóng mà tiền thật đã về" | User trả sát lúc đơn bị đóng → mất tiền không ai biết. Cách sửa: chặn từ gốc theo quyết định #13 (xác nhận link đã chết rồi mới ghi), webhook cho đơn đã đóng chỉ còn là báo động. |
| R2 ⚠️ | Thu sai số tiền chỉ `log.warn`, và **trả dư bị coi như sai số tiền** | `markPaid`: nhánh `!Objects.equals(...)` | Thu 40.000 cho đơn 49.000: đơn kẹt rồi hết hạn. Thu **52.000** cho đơn 49.000: user trả dư mà **không nhận hàng**, chỉ có một dòng log. Sửa: thiếu thì không giao (Giai đoạn 2), đủ hoặc dư thì giao và lưu `amount_paid` (#20). |
| R3 ⚠️ | Nhiều chỗ so số tiền **yêu cầu** (`getAmount`) thay vì số tiền **đã thu** (`getAmountPaid`) | `closeUnpaid`/`syncPending` gọi `markPaid(payment, link.getAmount())`; `PaymentLink` có `getAmountPaid()` (đã `javap`) | `getAmount()` luôn bằng số ta yêu cầu nên phép so sánh luôn đúng: link `PAID` với số tiền thu khác vẫn được chấp nhận qua polling. |
| R4 ⚠️ | Webhook chưa đối chiếu `paymentLinkId` với `payos_link_id` đã lưu | `processWebhook` chỉ tìm theo `orderCode` | Chữ ký chỉ chứng minh payOS gửi, không chứng minh khớp link hiện tại; sau khi thay link, webhook của link cũ vẫn kích hoạt đơn mới. Chỉ so khi `payos_link_id` đã lưu và webhook có `paymentLinkId` (chưa xác minh payOS luôn gửi trường này). |
| R5 ⚠️ | User bị xoá khi nâng cấp → webhook trả 5xx vĩnh viễn | `UserServiceImpl.upgradeToPremium` → `findByIdForUpdate` lọc `isDeleted=false` → `UserNotFoundException` → rollback | Sửa theo #10. |
| R6 | `createPayment` không tự kiểm tra đầu vào | Tin hoàn toàn người gọi; chỉ có `CHECK amount > 0` ở DB | Module khác gọi sai (amount 0, `referenceId` null) thì chỉ phát hiện khi INSERT lỗi hoặc đã gọi payOS. |
| R7 | Giá tối thiểu chưa kiểm tra | `PremiumPlanProperties` chỉ `@Positive`; mốc tối thiểu 2000 do user cung cấp, **chưa đối chiếu** với tài liệu payOS | Đặt giá 500 trong `.env`: app chạy, payOS từ chối lúc user bấm mua. Đã chốt mốc 2000 VND (quyết định #16). |
| R8 | `PayOsProperties` âm thầm đổi `expiredMinutes <= 0` thành 15 | compact constructor `PayOsProperties.java` | `.env` gõ nhầm `-5` không báo lỗi. |
| R9 | `orderCode` từ sequence bắt đầu 100000 ở mọi môi trường | `V16__payments_and_premium.sql` | Nếu hai môi trường dùng chung khoá payOS thì hai DB cùng sinh `100001`. Đã chốt #15: mỗi môi trường một khoá payOS riêng nên không phải sửa code. |
| R10 | Listener ném `IllegalArgumentException` trần khi `referenceId` sai | `PremiumPaymentListener.onPaymentPaid`: `PremiumPlan.valueOf` | Rollback đúng nhưng log không có `order_code`, không có test cho `null`/rỗng. |
| R11 | Chưa có test đa luồng nào | `src/test` | "Chỉ một đơn `PENDING`", "giao hàng đúng một lần" mới chỉ được chứng minh bằng test đơn luồng. |
| R12 ⚠️ | **Đọc không khoá rồi mới khoá: giao hàng hai lần** | `PaymentServiceImpl.getStatus`: `findByOrderCodeAndUserId` (`:71`) rồi `syncPending` → `findByOrderCodeForUpdate` (`:167`); bản `cancel` trong plan này cũng làm vậy; `Payment` không có `@Version` | Webhook ghi `PAID` và commit xen giữa hai truy vấn. Hibernate không làm mới entity đã nằm trong persistence context nên `payment.getStatus()` vẫn là `PENDING` cũ; `getLink` báo `PAID` → `markPaid` chạy lần hai → `upgradeToPremium` cộng thêm tháng. |
| R13 ⚠️ | **Thứ tự khoá ngược gây deadlock** | `createPayment` giữ khoá user (`UserServiceImpl.createPremiumUpgrade`) rồi đọc/khoá đơn (`PaymentServiceImpl.java:60`); webhook khoá đơn rồi `PremiumPaymentListener` → `upgradeToPremium` khoá user | Hai bên cùng chạy: mỗi bên giữ một khoá và chờ khoá kia → PostgreSQL huỷ một giao dịch (webhook rollback và phải gửi lại, hoặc user nhận 500). Đây là phát hiện thêm khi xử lý R12. |

## 3. Hướng giải quyết / code xem trước

**Hàm dùng chung** (thay `resolveOverdue`, `PaymentServiceImpl.java:186-201`):

```java
// Số tiền payOS đã thu trên link: lấy số lớn hơn giữa tổng các lần chuyển và trường tổng hợp (#14).
static long receivedOf(PaymentLink link) {
    long sum = link.getTransactions() == null ? 0
            : link.getTransactions().stream().mapToLong(t -> t.getAmount() == null ? 0 : t.getAmount()).sum();
    long reported = link.getAmountPaid() == null ? 0 : link.getAmountPaid();
    return Math.max(sum, reported);
}

// Đơn chưa trả: đủ tiền thì PAID; có một phần tiền thì giữ nguyên; chưa có đồng nào thì huỷ link rồi ghi targetStatus.
// Lỗi payOS thì ném, đơn giữ PENDING.
private void closeUnpaid(Payment payment, PaymentStatus targetStatus) {
    PaymentLink link = payOsClientHelper.getLink(payment.getOrderCode());
    long received = receivedOf(link);
    if (received >= payment.getAmount()) {                         // R3: số tiền ĐÃ THU
        markPaid(payment, received);
        return;
    }
    if (received > 0 || link.getStatus() == PaymentLinkStatus.PAID) {   // có tiền một phần, hoặc link PAID mà thiếu số liệu
        log.error("[PAYMENT-RECONCILE] link đã nhận {} < {} (status {}), không đóng đơn: orderCode={}",
                received, payment.getAmount(), link.getStatus(), payment.getOrderCode());
        throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);   // giữ PENDING (Giai đoạn 2: PARTIALLY_PAID)
    }
    if (!isDead(link.getStatus())) {                                // chưa nhận đồng nào nhưng link còn sống
        PaymentLink cancelled = payOsClientHelper.cancelLink(payment.getOrderCode());   // R1: đọc lại kết quả, không tin "không ném lỗi"
        long afterCancel = receivedOf(cancelled);
        if (afterCancel >= payment.getAmount()) {                   // tiền vào đúng lúc huỷ: ghi PAID, không huỷ
            markPaid(payment, afterCancel);
            return;
        }
        if (afterCancel > 0 || !isDead(cancelled.getStatus())) {   // vừa có tiền vào một phần, hoặc huỷ chưa có hiệu lực
            throw new PaymentException(ErrorCode.PAYMENT_PROVIDER_ERROR);   // giữ PENDING
        }
    }
    payment.setStatus(targetStatus);                                // chỉ tới đây khi chưa có tiền và link chắc chắn đã chết
}

private boolean isDead(PaymentLinkStatus status) {
    return status == PaymentLinkStatus.CANCELLED || status == PaymentLinkStatus.EXPIRED
            || status == PaymentLinkStatus.FAILED;                 // FAILED: ngữ nghĩa chưa xác minh, an toàn vì đã kiểm tra received == 0 ở trên
}
```

- `expireIfOverdue` gọi `closeUnpaid(payment, EXPIRED)`.
- Link đã chết sẵn ở payOS thì vẫn đặt `targetStatus` (quá hạn → `EXPIRED`, thay thế/huỷ → `CANCELLED`).

**`createPayment`** (cùng transaction với khoá dòng user ở module `user`):

```java
Payment pending;
try {
    pending = paymentRepository.findPendingForUpdateNoWait(userId, type, PENDING)   // R12 + R13: khoá ngay, NOWAIT
            .orElse(null);
} catch (PessimisticLockingFailureException ex) {                          // đơn đang bị webhook/job khoá
    throw new BusinessException(ErrorCode.REQUEST_IN_PROGRESS);            // → 409; transaction rollback, nhả cả khoá user
}
if (pending == null) return createNewPayment(req);

closeUnpaid(pending, isOverdue(pending) ? PaymentStatus.EXPIRED : PaymentStatus.CANCELLED);
if (pending.getStatus() != PaymentStatus.EXPIRED && pending.getStatus() != PaymentStatus.CANCELLED) {
    return toCreateRes(pending);          // PAID (hoặc PENDING nếu markPaid từ chối vì thiếu tiền): không tạo đơn mới
}
paymentRepository.flush();                // đẩy trạng thái cũ xuống DB trước, tránh vướng unique index
return createNewPayment(req);
```

**`createNewPayment`** (#22), ngay sau khi `payOsClientHelper.createLink(...)` trả về:

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCompletion(int status) {
        if (status == STATUS_ROLLED_BACK) {                   // đơn không được lưu nhưng link đã tồn tại bên payOS
            try { payOsClientHelper.cancelLink(orderCode); }
            catch (Exception ex) { log.error("[PAYMENT-RECONCILE] không huỷ được link mồ côi: orderCode={}", orderCode, ex); }
        }
    }
});
```

**`cancel(userId, orderCode)`**:

```java
if (!paymentRepository.existsByOrderCodeAndUserId(orderCode, userId)) {    // R12: không nạp entity trước khi khoá
    throw new PaymentNotFoundException();                                  // đơn người khác → 404 NOT_FOUND
}
Payment payment = paymentRepository.findByOrderCodeForUpdate(orderCode)    // lần nạp ĐẦU TIÊN, có khoá
        .orElseThrow(PaymentNotFoundException::new);
if (payment.getStatus() == PaymentStatus.PENDING) {
    closeUnpaid(payment, PaymentStatus.CANCELLED);          // lỗi payOS → PAYMENT_PROVIDER_ERROR (502)
}
return paymentMapper.toStatusRes(payment);                  // đã PAID / EXPIRED / CANCELLED thì trả nguyên trạng thái
```

**`getStatus`** (sửa lỗi có sẵn, R12): kiểm tra sở hữu bằng `existsByOrderCodeAndUserId`; nếu đơn thuộc user thì **chỉ** nạp qua `findByOrderCodeForUpdate` (lần nạp đầu tiên, có khoá) rồi quyết định `PENDING` hay không dựa trên bản vừa nạp có khoá; trả `toStatusRes` từ chính object đó.

**`syncPending`** (sau khi `getLink`): `receivedOf(link) >= amount` → `markPaid(payment, receivedOf(link))`; `received == 0` và link `CANCELLED`/`EXPIRED`/`FAILED` → ghi `CANCELLED`/`EXPIRED`/`EXPIRED` tương ứng; `received > 0` mà chưa đủ → giữ `PENDING`, log ERROR (Giai đoạn 2 xử lý); còn lại giữ nguyên.

**Webhook và `markPaid`** (R1-R4):

```java
// processWebhook sau verifyWebhook
Payment payment = repo.findByOrderCodeForUpdate(orderCode).orElse(null);
if (payment == null) {                                                // #21
    if (orderCode >= FIRST_ORDER_CODE) {
        log.error("[PAYMENT-RECONCILE] không có đơn cho orderCode={}, amount={}, paymentLinkId={} (link mồ côi?)", ...);
    } else {
        log.info("payOS webhook thử/không phải của hệ thống: orderCode={}", orderCode);
    }
    return;
}
if (!sameLink(payment, data.getPaymentLinkId())) {               // R4
    log.error("[PAYMENT-RECONCILE] link không khớp: orderCode={}", orderCode); return;
}
markPaid(payment, data.getAmount());

private void markPaid(Payment p, Long amountPaid) {
    if (p.getStatus() == PAID) return;                            // replay hợp lệ: bỏ qua, không đụng amount_paid
    if (p.getStatus() != PENDING) {                              // R1: đơn đã đóng mà vẫn có tiền = bất biến bị vỡ
        log.error("[PAYMENT-RECONCILE] nhận tiền cho đơn đã {}: orderCode={}, paid={}",   // báo động bất kể đủ hay thiếu tiền
                p.getStatus(), p.getOrderCode(), amountPaid);
        return;                                                  //    KHÔNG giao hàng, KHÔNG ghi amount_paid
    }
    if (amountPaid == null || amountPaid < p.getAmount()) {      // R2: thiếu tiền, không giao hàng (Giai đoạn 2 xử lý)
        log.error("[PAYMENT-RECONCILE] thiếu tiền: expected={}, paid={}", ...); return;
    }
    if (amountPaid > p.getAmount()) {                            // #20: dư tiền vẫn giao hàng, không hoàn
        log.info("Payment {} trả dư {}", p.getOrderCode(), amountPaid - p.getAmount());
    }
    p.setAmountPaid(amountPaid);                                 // chỉ ghi khi đơn thật sự thành PAID
    p.setStatus(PAID); p.setPaidAt(now); publish(PaymentPaidEvent)
}
```

- R6: `PaymentCreateReq` ném `IllegalArgumentException` trong compact constructor; `PayOsClientHelper.createLink` kiểm tra tương tự trước khi gọi SDK.
- R7/R8: `@Validated` + `@Min` cho `PayOsProperties`/`PremiumPlanProperties`; bỏ chuyển đổi âm thầm.
- R10 / #10: `PremiumPaymentListener` bắt `UserNotFoundException` → log `[PAYMENT-RECONCILE]` ERROR rồi trả về bình thường; `referenceId` sai → bắt `IllegalArgumentException` ném lại `IllegalStateException` kèm `orderCode`.

**⚠️ API contract (ảnh hưởng app Flutter)**

| Thay đổi | Ảnh hưởng FE |
|---|---|
| `POST /users/me/premium-upgrades` khi còn đơn chờ: trả **đơn mới** (`order_code` khác), không còn trả đơn cũ. | FE luôn dùng `order_code`/`checkout_url` trong response mới nhất. Mỗi lần user bấm sinh một `Idempotency-Key`, retry gửi lại đúng key; khoá nút khi đang chờ. |
| Mới `DELETE /payments/{order_code}` → `PaymentStatusRes`. | Thêm nút "Huỷ thanh toán". Kiểm tra `status` trong response (có thể là `PAID`). |
| Polling có thể trả `CANCELLED`/`EXPIRED` sớm hơn trước. | FE xử lý đủ 4 trạng thái. |
| `POST /users/me/premium-upgrades` có thể trả **`409 REQUEST_IN_PROGRESS`** khi đơn đang được webhook/job xử lý (#19). | FE tự thử lại sau ~1-2 giây với **cùng** `Idempotency-Key` (lỗi không được cache nên thử lại chạy bình thường) và không báo lỗi cho user ở lần thử đầu. |

Thêm cột `amount_paid` (migration `V17`) và các trường `amount_paid`, `excess_amount` trong `GET /payments`, `GET /payments/{order_code}`, `GET /payments/admin/**`; FE hiển thị khi cần đối chiếu khiếu nại.

**Test viết đỏ trước, đỏ vì khẳng định sai ở:**

*Huỷ đơn / link mới* (`PaymentServiceImplTest`):
- còn đơn chờ chưa hết hạn: `verify(payOsClientHelper).cancelLink(...)` và `createLink(...)` được gọi, đơn cũ `CANCELLED` (hiện code không gọi hai hàm này);
- payOS báo đơn cũ `PAID` lúc tạo đơn: trả đơn `PAID`, `createLink` **không** được gọi;
- huỷ link cũ lỗi: ném `PaymentException`, đơn cũ vẫn `PENDING`, `createLink` không được gọi;
- `cancel`: đơn `PENDING` → `CANCELLED` + `cancelLink`; đơn của user khác → `PaymentNotFoundException`; đơn đã `PAID` → trả `PAID`, không gọi `cancelLink`; payOS báo `PAID` lúc huỷ → ghi `PAID`;
- `getStatus` khi payOS báo `CANCELLED` → ghi và trả `CANCELLED`;
- `cancelLink` trả link vẫn `PENDING`/`PROCESSING` (huỷ chưa có hiệu lực) → ném lỗi, đơn giữ `PENDING`, không tạo đơn mới (đỏ vì code hiện tại ghi `EXPIRED` bất kể kết quả huỷ);
- `cancelLink` trả link `PAID` → đơn ghi `PAID` + phát event, không ghi `CANCELLED`/`EXPIRED`;
- webhook hợp lệ cho đơn đã `CANCELLED`/`EXPIRED` → đơn không đổi, không phát event, có log báo động;
- webhook hợp lệ với `orderCode` không có trong DB: `orderCode = 100000` và `Long.MAX_VALUE` → log `ERROR [PAYMENT-RECONCILE]`, vẫn không ném lỗi, không event; `orderCode = 123` và `99999` → chỉ `INFO` (đỏ vì code hiện tại chỉ có `INFO` cho mọi ca);
- **thứ tự kiểm tra trong `markPaid`**: webhook cho đơn `CANCELLED`/`EXPIRED` với số tiền **thiếu**, **đủ**, **dư** và `null` đều phải ra log "nhận tiền cho đơn đã ..." (báo động bất biến vỡ), **không** ra log "thiếu tiền", và `amount_paid` vẫn `null` (đỏ với thứ tự cũ vì đơn đã đóng bị ghi `amount_paid`/log sai loại); webhook gửi lại cho đơn đã `PAID` không đổi `amount_paid` đã lưu dù số tiền trong lần gửi lại khác;
- `findPendingForUpdateNoWait` ném `CannotAcquireLockException` (và `PessimisticLockingFailureException` trần) → `createPayment` ném `BusinessException` mã `REQUEST_IN_PROGRESS` (status 409), **không** gọi `createLink`/`cancelLink` (đỏ vì hiện không bắt lỗi nên ra `CannotAcquireLockException` thô).

*Validate tiền:*
1. **Webhook** (`@ParameterizedTest`): `(trạng thái đơn) × (số tiền thu: bằng đơn, đơn-1, đơn+1, gấp đôi, 0, âm, null) × (code: "00", "01", null) × (success: true, false, null)`. Kết quả mong đợi theo bảng: *đơn `PENDING` + (`bằng đơn` | `đơn+1` | `gấp đôi`) + "00"/"00" + success* → **`PAID`**, phát event đúng một lần, `amount_paid` = số thực nhận (hai ca `đơn+1` và `gấp đôi` là trả dư theo #20 nên vẫn `PAID`); *đơn `PENDING` + (`đơn-1` | `0` | âm | `null`)* → không đổi trạng thái, không event, `amount_paid` vẫn `null`, có log `[PAYMENT-RECONCILE] thiếu tiền`; đơn `PAID` không phát lại và không đổi `amount_paid`; **đơn `CANCELLED`/`EXPIRED` ở mọi mức tiền** không đổi trạng thái, không event, `amount_paid` vẫn `null`, có log báo động `nhận tiền cho đơn đã ...`; `code`/`success` sai → bỏ qua trước cả bước tìm đơn. Log kiểm tra bằng `OutputCaptureExtension`.
2. **Replay**: cùng webhook 5 lần liên tiếp → `verify(eventPublisher, times(1))`.
3. **Số tiền đã thu vs yêu cầu** (R3, #14): `receivedOf` với các tổ hợp (`transactions` = [], [3.000], [3.000, 2.000], chứa `amount = null`) × (`amountPaid` = null, 0, 3.000, 5.000, và khác với tổng giao dịch) → luôn lấy số lớn hơn; `received` nhỏ hơn số đơn (hoặc `null`) → không `PAID`, không event; `received` bằng hoặc lớn hơn số đơn → `PAID` (cả polling lẫn `closeUnpaid`); riêng ca `getAmount()` bằng số đơn nhưng tiền thật nhận nhỏ hơn phải **không** `PAID` (đỏ với code cũ vì code cũ so `getAmount()`); link báo `PAID` mà không có số liệu tiền → giữ `PENDING` + log ERROR.
4. **Link không khớp** (R4): webhook hợp lệ chữ ký nhưng `paymentLinkId` khác → bỏ qua + log.
5. **Ma trận chuyển trạng thái**: mọi `PaymentStatus` × mọi sự kiện (`webhook`, `polling`, `cancel`, `expire`, `createPayment`); `PAID` là trạng thái cuối không bao giờ bị đổi.
6. **Biên thời gian**: `expiresAt == now`, `now ± 1ms` (dùng `Clock` tiêm vào, quyết định #17).
7. **Trả dư (#20)**: thu `amount + 1`, `amount + 2.000`, `amount × 2` → `PAID`, giao hàng đúng một lần, `amount_paid` lưu đúng số thực nhận, `excess_amount` đúng, API user và admin trả `amount_paid`/`excess_amount`; qua cả ba đường webhook, polling (`getAmountPaid()` của link) và `closeUnpaid`. Thu `amount - 1` → không `PAID`, không giao hàng.
8. **Biên số tiền**: amount `0`, `-1`, `Long.MAX_VALUE`; DB `CHECK` từ chối `0`/âm; `PaymentCreateReq` từ chối trước khi chạm DB.
9. **Premium**: user bị xoá → đơn `PAID`, không ném lỗi; đơn cũ trả muộn khi đã premium → cộng dồn; `months` 0/âm; qua mốc tháng ngắn (31/01 + 1 tháng); premium vô thời hạn.
10. **Cấu hình** (`ApplicationContextRunner`): thiếu/âm/0/rỗng → context **không khởi động**; đủ → khởi động; mọi `PremiumPlan` có giá.
11. **Đa luồng** (`PaymentConcurrencyIntegrationTest`, `ExecutorService` + `CountDownLatch`, PostgreSQL thật):
    - 10 request `POST /users/me/premium-upgrades` đồng thời cùng user → cuối cùng đúng **1** dòng `PENDING`, không lỗi `DataIntegrityViolation` lọt ra ngoài;
    - 5 webhook cùng `order_code` đồng thời → `plan_expires_at` chỉ cộng đúng **một lần**;
    - webhook và polling cùng lúc cho một đơn → giao hàng đúng một lần;
    - webhook đồng thời với tạo đơn mới (huỷ đơn cũ) → không mất tiền, mỗi lần chạy rơi vào đúng một trong **ba** kết quả hợp lệ: (a) đơn cũ `PAID` và không tạo đơn mới; (b) đơn cũ `CANCELLED` và đơn mới `PENDING`; (c) `createPayment` bị `NOWAIT` từ chối nên `POST` nhận `409 REQUEST_IN_PROGRESS`, đơn cũ do webhook xử lý (`PAID`) và gọi lại cùng `Idempotency-Key` thì ra (a). Không bao giờ có `500`, `DeadlockLoserDataAccessException` hay hai đơn `PENDING`.
12. **IDOR/phân quyền**: mọi endpoint `/payments/**` với token user khác, không token, token admin thiếu `PAYMENT_READ`, user thường gọi `/payments/admin/**`.
13. **Validate HTTP** (`PaymentValidationIntegrationTest`): `order_code` không phải số / quá lớn / âm; `status`/`type` lọc sai; `page_size` vượt trần; `from_date` sai định dạng; body thiếu/thừa trường; webhook body rỗng/JSON hỏng.

**Test chứng minh bất biến #13.** Bất biến gồm hai vế loại trừ nhau, tuỳ huỷ hay trả tiền thắng cuộc đua: (1) đơn `CANCELLED`/`EXPIRED` ⇒ link payOS đã chết và **không có đồng nào vào**; (2) tiền đã vào (lúc link còn sống) ⇒ đơn `PAID` + giao hàng đúng một lần, **không bao giờ** `CANCELLED`/`EXPIRED`. Không tồn tại trường hợp "link đã chết mà vẫn có tiền vào"; nếu xuất hiện thì đơn không đổi sang `PAID`, chỉ bắn log báo động.

Cách làm: `FakePayOsClient` (extends `PayOsClientHelper`, ghi đè `createLink`/`getLink`/`cancelLink`/`verifyWebhook`) mô phỏng luật payOS, mọi thao tác trên mỗi link được đồng bộ để quyết định *nguyên tử*:
- link `PENDING`: `userPays()` → `PAID` (ghi nhận `moneyReceived = amount`); `cancelLink` → `CANCELLED`;
- link `PAID`: `cancelLink` **ném lỗi** (giống payOS từ chối huỷ link đã trả — giả định, **chưa xác minh** với payOS thật); `userPays()` bị từ chối;
- link `CANCELLED`/`EXPIRED`: `userPays()` bị từ chối (không có tiền vào);
- có thể bật cờ chèn độ trễ/`CountDownLatch` vào giữa `getLink` và `cancelLink` để dựng race.

*I1. Unit — `PaymentInvariantTest`* (`@ParameterizedTest`, `PayOsClientHelper` mock):
- Với mỗi điểm vào (`createPayment` khi còn đơn chờ, `cancel`, `expireIfOverdue`, `getStatus` qua `syncPending`) × **mỗi giá trị** của `PaymentLinkStatus` (kể cả `FAILED`, `UNDERPAID`, `PROCESSING`) từ `getLink` × số tiền đã nhận (0, một phần, đủ, dư) × kết quả `cancelLink` (mỗi `PaymentLinkStatus`, có/không có tiền vừa vào, hoặc ném lỗi): khẳng định *trạng thái local cuối cùng thuộc `{CANCELLED, EXPIRED}` chỉ khi `received == 0` và link đã chết (từ `getLink` hoặc từ kết quả `cancelLink`)*; `received >= amount` thì `PAID` và phát event đúng một lần; `0 < received < amount` hoặc huỷ chưa hiệu lực hoặc lỗi thì giữ `PENDING`, **không** gọi `createLink`. Ca riêng cho `FAILED` với `received == 0`: đơn đóng được (không kẹt `PENDING` mãi).
- Webhook hợp lệ cho đơn `CANCELLED`/`EXPIRED`: đơn không đổi, không event, có log `[PAYMENT-RECONCILE]` ERROR.

*I4. Integration — `PaymentLockOrderIntegrationTest`* (PostgreSQL thật; viết đỏ trước, đỏ với code hiện tại):
- **Đọc cũ sau khoá (R12)**, tái hiện *xác định* không cần đua luồng: `@MockitoSpyBean PaymentRepository`; stub `existsByOrderCodeAndUserId`/`findByOrderCodeForUpdate` theo `doAnswer` sao cho ngay trước lần nạp có khoá, một transaction `REQUIRES_NEW` khác ghi `PAID` và commit (giả lập webhook xen giữa). Gọi `getStatus` và `cancel`; khẳng định `PaymentPaidEvent` phát đúng **một** lần (bản webhook), `plan_expires_at` cộng đúng một lần, kết quả trả về `PAID`.
- **Deadlock (R13)**: lặp 100 lần chạy đồng thời `POST /users/me/premium-upgrades` (huỷ đơn cũ, tạo đơn mới) và webhook `PAID` của chính đơn cũ; khẳng định không có `DeadlockLoserDataAccessException`/500 lọt ra ngoài, kết quả luôn là một trong hai hợp lệ (đơn cũ `PAID`; hoặc đơn cũ `CANCELLED` + đơn mới `PENDING`), và nếu `createPayment` bị `NOWAIT` từ chối thì nhận **đúng** `409` với `error.code = REQUEST_IN_PROGRESS` (không bao giờ `500`). Thêm ca xác định: giữ khoá đơn trong một transaction của test (`FOR UPDATE`) rồi gọi `POST /users/me/premium-upgrades` → 409; nhả khoá rồi gọi lại cùng `Idempotency-Key` → 200 và tạo đơn bình thường.

- **Link mồ côi (#22)**: dùng `FakePayOsClient` + `@MockitoSpyBean PaymentRepository` ép lỗi (ném `RuntimeException`) ngay sau `createLink` làm transaction rollback; khẳng định `POST /users/me/premium-upgrades` trả lỗi, DB không có đơn, và link fake **ở trạng thái `CANCELLED`, `moneyReceived == 0`**, `userPays()` vào link đó bị từ chối. Ca phụ: `cancelLink` bù trừ ném lỗi → có log `[PAYMENT-RECONCILE]`, request vẫn trả lỗi ban đầu (không bị nuốt). Rồi giả lập webhook cho link mồ côi đã bị huỷ không thể xảy ra, nên thêm ca webhook cho `orderCode` lạ ≥ 100000 → `200` + log `ERROR`.

*I2. Integration — `PaymentRaceIntegrationTest`* (PostgreSQL thật + `FakePayOsClient`), lặp 50 lần với thứ tự xen kẽ khác nhau:
- Thread A gọi `DELETE /payments/{order_code}` (bị giữ giữa `getLink` và `cancelLink` bằng latch); thread B gọi `fake.userPays(orderCode)`; sau đó giao webhook nếu fake báo có tiền. Khẳng định cuối: **hoặc** (đơn `CANCELLED`, `fake.moneyReceived == 0`, user vẫn `FREE`) **hoặc** (đơn `PAID`, `fake.moneyReceived == amount`, user `PREMIUM`, `plan_expires_at` cộng đúng một lần). Không bao giờ ra (`CANCELLED` + có tiền).
- Tương tự cho `POST /users/me/premium-upgrades` (huỷ đơn cũ để tạo đơn mới) chạy đồng thời với `userPays` đơn cũ; và cho `PaymentExpiryJob` quá hạn chạy đồng thời với `userPays`.

*I3. Integration — `PaymentInvariantFuzzIntegrationTest`* (PostgreSQL thật + `FakePayOsClient` + `MutableClock` tiêm vào cả service và job, quyết định #17):
- Mỗi seed (50 seed cố định, in seed khi lỗi để tái hiện) chạy 200 bước ngẫu nhiên từ tập: tạo đơn (gói ngẫu nhiên), huỷ đơn, polling, `userPays` một link còn sống, giao webhook (kể cả giao lại), chạy job quá hạn, tua thời gian, user bị xoá, ép rollback sau `createLink`.
- Sau **mỗi bước** kiểm tra: (1) đơn `CANCELLED`/`EXPIRED` ⇒ link fake không `PAID` và `moneyReceived == 0`; (2) mỗi link `PAID` trong fake ⇒ đơn `PAID` hoặc webhook của nó chưa được giao; sau khi giao hết webhook thì đơn đó phải `PAID`; (3) mỗi user có tối đa một đơn `PENDING` và tối đa một link còn sống trong fake, và **mọi link còn sống trong fake đều có đơn `PENDING` tương ứng trong DB** (không có link mồ côi; thao tác "transaction rollback sau createLink" thuộc tập bước ngẫu nhiên); (4) tổng số lần giao hàng = số link `PAID` (không thiếu, không thừa); (5) `plan_expires_at` của user tương ứng tổng số tháng đã mua.
- Một bước làm bất biến sai thì test đỏ và in chuỗi thao tác + seed.

`PaymentCancelIntegrationTest` (`@MockitoBean PayOsClientHelper`): `DELETE` đơn của mình → 200 `CANCELLED` + DB; đơn của người khác → 404; không token → 401; huỷ xong tạo đơn mới được ngay.

## 4. Danh sách công việc chi tiết

1. Viết test đỏ: sửa 2 test cũ, thêm ca huỷ đơn và ma trận validate vào `PaymentServiceImplTest`, test mới cho `PaymentCreateReq`, cấu hình, listener, `PaymentLockOrderIntegrationTest` (đỏ với code hiện tại).
2. Migration `V17__payment_amount_paid.sql`; thêm `amountPaid` vào entity/DTO/mapper.
3. `ClockConfig`; tiêm `Clock` vào `PaymentServiceImpl`, `UserServiceImpl`, `PaymentExpiryJob`; sửa `PaymentExpiryJobTest`; thêm `MutableClock`.
4. Sửa `PaymentServiceImpl`: khoá-trước-khi-đọc (`getStatus`, `cancel`, `createPayment` NOWAIT + đổi lỗi khoá sang 409), `closeUnpaid`, `syncPending`, `expireIfOverdue`, `processWebhook` (log link mồ côi), `markPaid`, `createNewPayment` (huỷ link bù trừ khi rollback).
5. Thêm `cancel` vào `PaymentService`; `DELETE` vào `PaymentController`; thêm truy vấn vào `PaymentRepository`; sửa comment `UserService`.
6. Siết `PaymentCreateReq`, `PayOsClientHelper`, `PayOsProperties`, `PremiumPlanProperties`, `UserServiceImpl.upgradeToPremium`, `PremiumPaymentListener`.
7. Viết `FakePayOsClient`, `PaymentInvariantTest`, `PaymentRaceIntegrationTest`, `PaymentInvariantFuzzIntegrationTest`, `PaymentCancelIntegrationTest`, `PaymentConcurrencyIntegrationTest`, `PaymentValidationIntegrationTest`; sửa `UserPremiumUpgradeIntegrationTest`; thêm ca vào test webhook/repository.
8. Cập nhật `docs/api/payment.md`, `docs/api/user.md`, `docs/design/user/api.md`, đánh dấu quyết định #12 của plan premium đã bị thay thế.
9. Thêm `bruno/15-Payments/User/03-Cancel-Payment.yml`.
10. Biên dịch, `git diff` đối chiếu mục 1, báo cáo.

## Ngoài phạm vi

- Hoàn tiền tự động, màn đối soát cho admin (chỉ để lại log `[PAYMENT-RECONCILE]`); admin vẫn chỉ đọc.
- Hoàn tiền / huỷ gói đã trả (giữ nguyên quyết định #17 của `plan-premium-upgrade-payment.md`).
- Giới hạn tần suất tạo đơn (mỗi lần bấm khi có đơn chờ tốn `getLink` + `cancelLink` + `create`). Muốn chống spam thì làm plan riêng.
- Gọi API payOS thật trong test (luôn mock hoặc dùng `FakePayOsClient`). Giới hạn: test chỉ chứng minh server hành xử đúng *nếu* payOS đúng luật mô phỏng ở `FakePayOsClient` (đặc biệt "huỷ link đã trả bị từ chối"); cần đối chiếu một lần với tài liệu/sandbox payOS.
- ⚠️ `application-dev.yml` vẫn chứa khoá payOS/Cloudinary mặc định; không sửa ở đây.

## Kiểm tra

- Hỏi bạn trước khi chạy test; không chạy full test.
- Lệnh chạy test liên quan (integration cần Docker):

```powershell
mvn test -Dtest=PaymentServiceImplTest,PaymentInvariantTest,PaymentAdminServiceImplTest,PaymentExpiryJobTest,PaymentCreateReqTest,PayOsPropertiesTest,PremiumPlanPropertiesTest,UserServiceImplPremiumTest,PremiumPaymentListenerTest
```

```powershell
mvn test -Dtest=PaymentLockOrderIntegrationTest,PaymentRaceIntegrationTest,PaymentInvariantFuzzIntegrationTest,PaymentCancelIntegrationTest,PaymentConcurrencyIntegrationTest,PaymentValidationIntegrationTest,UserPremiumUpgradeIntegrationTest,PaymentWebhookIntegrationTest,PaymentHistoryIntegrationTest,PaymentRepositoryTest
```

- Không còn tham chiếu tới hành vi cũ và không còn so sánh số tiền yêu cầu:

```powershell
rg -n "trả lại (đúng )?đơn|ReturnsSameLink|ReturnsSameOrder|reuseOrResolvePending|resolveOverdue" src docs bruno
rg -n "getAmount\(\)" src/main/java/com/datn/financeapp/module/pay
rg -n "findByOrderCodeAndUserId|findByUserIdAndTypeAndStatus" src/main/java
```

- Bạn tự chạy full test nếu cần kết quả tổng.

## Câu hỏi còn mở

Không còn.

## Bảng công việc

| # | Công việc | Trạng thái |
|---|---|---|
| 1 | Test đỏ (huỷ đơn, validate, khoá) | Hoàn thành |
| 2 | Migration `V17` + `amountPaid` trong entity/DTO/mapper | Hoàn thành |
| 3 | `ClockConfig`, tiêm `Clock`, `MutableClock`, sửa `PaymentExpiryJobTest` | Hoàn thành |
| 4 | `PaymentServiceImpl` | Hoàn thành |
| 5 | `PaymentService`, `PaymentController`, `PaymentRepository`, comment `UserService` | Hoàn thành |
| 6 | Siết dto/helper/config/listener/`upgradeToPremium` | Hoàn thành |
| 7 | `FakePayOsClient` + test bất biến (unit, race, fuzz) + integration test | Hoàn thành |
| 8 | Tài liệu + đánh dấu plan cũ | Hoàn thành |
| 9 | Bruno | Hoàn thành |
| 10 | Biên dịch, đối chiếu diff, báo cáo | Hoàn thành |

---

## Giai đoạn 2: thanh toán thiếu tiền (bù trong thời hạn, quá hạn thì giữ tiền đã nhận)

> **Chỉ code sau khi Giai đoạn 1 xong và câu hỏi G2-A (xác minh payOS) đã có kết quả.** Giai đoạn 1 có thể code ngay, không bị chặn bởi phần này.

**Bối cảnh giai đoạn 2:** link payOS có số tiền cố định nhưng user vẫn chuyển lệch được (nhiều app ngân hàng cho sửa số tiền sau khi quét QR); SDK có sẵn `PaymentLinkStatus.UNDERPAID` (đã `javap` payOS 2.0.1). Giai đoạn 1 chỉ xử lý tạm bằng quyết định #14 (giữ `PENDING`, ném lỗi), hậu quả là đơn quá hạn mà link vẫn `UNDERPAID` thì job lỗi mãi và user không tạo được đơn mới. Yêu cầu của user: trả chưa đủ (VD đơn 10.000, trả 3.000) thì hết hạn sẽ thông báo và yêu cầu bù trong N thời gian; không bù thì giữ luôn số tiền đã nhận. Giai đoạn 2 **thay thế** quyết định #14 của Giai đoạn 1.

### Quyết định đã chốt (Giai đoạn 2)

| # | Nội dung |
|---|---|
| 1 | Khi đã nhận tiền nhưng chưa đủ: đơn chuyển sang trạng thái mới `PARTIALLY_PAID`, lưu `amount_paid` (cộng dồn các khoản đã nhận, cột đã có từ `V17`) và `topup_deadline = now + N`; **gửi thông báo** cho user số tiền còn thiếu và hạn bù. |
| 2 | User bù bằng **link payOS mới với số tiền còn thiếu** (user chốt), không dùng lại link cũ. Mỗi link có `order_code` riêng nên cần bảng `payment_links` (mục 3). |
| 3 | Trả đủ (tổng các link ≥ số đơn) trong hạn bù thì đơn `PAID` và giao hàng, hạn premium tính từ lúc **đủ tiền**. |
| 4 | **Hết hạn bù mà chưa đủ: giữ số tiền đã nhận, không hoàn** (user chốt). Đơn chuyển trạng thái cuối `FORFEITED`, không giao hàng. |
| 5 | Đơn `PARTIALLY_PAID` chiếm chỗ "đơn đang mở" của user (unique index bao gồm cả `PARTIALLY_PAID`): user **không** tạo được đơn mới cho tới khi đơn này `PAID` hoặc `FORFEITED`, để tránh trả hai nơi cho cùng một món. |
| 6 | Thời hạn bù đặt trong `.env`: `PAYMENT_TOPUP_SECONDS`, bắt buộc, không có mặc định (thiếu thì app không khởi động). **Giá trị đang dùng để test: `30` (30 giây)**, đổi sang giá trị thật (VD 86400 = 24 giờ) khi triển khai. Dùng **giây** (không phải phút) vì cần giá trị 30 giây để test. |
| 7 | Bất biến của Giai đoạn 1 (#13) vẫn đúng và áp dụng cho từng link: link đã chết thì không có tiền vào. |
| 8 | Có viết test (unit + integration + invariant với `FakePayOsClient`), có cập nhật tài liệu và Bruno. |
| 9 | **Nhắc lần hai trước khi hết hạn bù**: gửi một thông báo nhắc khi còn `PAYMENT_TOPUP_REMINDER_SECONDS` (tạm đặt `3600` = 1 giờ, user chốt) trước `topup_deadline`. Gửi tối đa một lần mỗi đơn (cột `topup_reminded_at`). Nếu thời hạn bù `<=` mốc nhắc (VD `PAYMENT_TOPUP_SECONDS = 30`) thì **không nhắc** vì thông báo ban đầu đã gần như là lúc hết hạn. |
| 10 | Phần thiếu nhỏ hơn mức tối thiểu 2000 VND (quyết định #16 của Giai đoạn 1): làm tròn số tiền link bù **lên 2000**; phần dư thêm đó theo quyết định #20 (không hoàn) (user xác nhận). |
| 11 | Chu kỳ chạy của `PaymentTopupExpiryJob` cấu hình được (`PAYMENT_TOPUP_JOB_SECONDS`, mặc định 60) và phải nhỏ hơn đáng kể so với `PAYMENT_TOPUP_SECONDS`; test với `30` giây thì đặt `10`. Bắt buộc để đơn quá hạn bù được đóng đúng lúc thay vì trễ tới 5 phút. |

> ⚠️ **Lưu ý của người viết plan (đã trao đổi với user, user vẫn chọn giữ tiền):** giữ tiền mà không giao hàng dễ phát sinh khiếu nại/tranh chấp với ngân hàng hoặc payOS; không phải tư vấn pháp lý. Giảm rủi ro bằng cách buộc FE hiển thị điều khoản "chuyển thiếu quá hạn bù sẽ không được hoàn" **trước khi** user thanh toán (xem mục 3, thay đổi API) và lưu bằng chứng (số tiền, thời điểm, thông báo đã gửi).

### G2.1 Các file sẽ thay đổi

Đường dẫn gốc `src/main/java/com/datn/financeapp/`.

**CSDL**

| File | Thay đổi |
|---|---|
| `src/main/resources/db/migration_v2/V19__payment_partial_topup.sql` | **Mới.** Bảng `payment_links`; cột `payments.topup_deadline`, `payments.topup_reminded_at`, `payments.forfeited_at`; mở `ck_payments_status` thêm `PARTIALLY_PAID`, `FORFEITED`; tạo lại `uq_payments_pending_per_user_type` gồm `PARTIALLY_PAID`; chuyển dữ liệu `order_code`/`payos_link_id` hiện có sang `payment_links`. ⚠️ Số version sau `V18__payment_webhook_logs.sql` (dashboard webhook); chưa sửa migration cũ. |

**Code**

| File | Thay đổi |
|---|---|
| `module/pay/enums/PaymentStatus.java` | Thêm `PARTIALLY_PAID`, `FORFEITED`. |
| `module/pay/entity/Payment.java` | Thêm `topupDeadline`, `topupRemindedAt`, `forfeitedAt` (`amountPaid` đã có từ Giai đoạn 1). |
| `module/pay/entity/PaymentLink.java` | **Mới.** Một link payOS của một đơn (`orderCode` unique, `amount`, `amountPaid`, `status`, `payosLinkId`). Tên khác `vn.payos...PaymentLink` nên import SDK phải dùng tên đầy đủ hoặc đổi tên entity thành `PaymentLinkRecord`. |
| `module/pay/repository/PaymentLinkRepository.java` | **Mới.** `findByOrderCode`, `findByOrderCodeForUpdate`. |
| `module/pay/repository/PaymentRepository.java` | Truy vấn khoá/tra cứu theo link; `findTopupExpiredIds(now, limit)`. |
| `module/pay/service/impl/PaymentServiceImpl.java` | Webhook/polling tra theo `payment_links`; cộng dồn `amount_paid`; chuyển `PARTIALLY_PAID`, tạo link bù, gửi thông báo; `PAID` khi đủ; `closeUnpaid`: nhánh `0 < received < amount` (hiện giữ `PENDING` và ném lỗi) đổi thành chuyển `PARTIALLY_PAID`. |
| `module/pay/service/PaymentService.java` | Thêm `createTopupLink(userId, orderCode)` (tạo lại link bù khi link cũ hết hạn), `findTopupExpired(...)`, `forfeitIfTopupExpired(orderCode)`. |
| `module/pay/scheduler/PaymentSyncJob.java` | **Mới.** Mỗi 1 phút hỏi payOS (`getLink`) cho các đơn `PENDING`/`PARTIALLY_PAID` còn hạn để phát hiện khoản thiếu mà không phụ thuộc webhook; tải nhỏ vì đơn chỉ sống tối đa `PAYOS_EXPIRED_MINUTES` + hạn bù; dùng `Clock` (#17). |
| `module/pay/scheduler/PaymentTopupExpiryJob.java` | **Mới.** Chạy mỗi `PAYMENT_TOPUP_JOB_SECONDS` (quyết định #11): (a) đơn `PARTIALLY_PAID` quá `topup_deadline` → `FORFEITED`; (b) đơn còn `<= PAYMENT_TOPUP_REMINDER_SECONDS` tới hạn và chưa nhắc → phát `PaymentTopupReminderEvent`, ghi `topup_reminded_at` (#9). Dùng `Clock` tiêm vào (#17 của Giai đoạn 1). |
| `module/pay/events/publisher/PaymentPartiallyPaidEvent.java` | **Mới.** Phát khi đơn chuyển `PARTIALLY_PAID`; module `notification` hoặc `user` nghe để gửi thông báo (không để `pay` import `notification`). |
| `module/pay/events/publisher/PaymentTopupReminderEvent.java` | **Mới.** Phát khi đến mốc nhắc (#9). |
| `module/pay/events/publisher/PaymentForfeitedEvent.java` | **Mới.** Phát khi `FORFEITED` để báo user. |
| `module/pay/config/PayOsProperties.java` hoặc `module/pay/config/PaymentTopupProperties.java` | **Mới/sửa.** `topupSeconds` `@Positive` bắt buộc; `topupReminderSeconds` (mặc định 3600, `@PositiveOrZero`); `topupJobSeconds` (mặc định 60, `@Positive`). |
| `module/pay/controller/PaymentController.java` | `POST /payments/{order_code}/topup-links` (tạo/làm mới link bù); `PaymentStatusRes` thêm trường (xem API). |
| `module/pay/dto/res/PaymentStatusRes.java`, `PaymentAdminRes.java`, `PaymentCreateRes.java` | Thêm `amountPaid`, `amountRemaining`, `topupDeadline`, `topupCheckoutUrl`. |
| `module/pay/mapper/PaymentMapper.java` | Cập nhật ánh xạ (MapStruct `unmappedTargetPolicy = ERROR`). |
| `module/notification/enums/NotiType.java` | Thêm `PAYMENT_TOPUP_REQUIRED`, `PAYMENT_TOPUP_REMINDER`, `PAYMENT_FORFEITED` (comment: `reference_id` là id đơn thanh toán). |
| `module/notification/events/listener/PaymentNotificationListener.java` | **Mới.** Nghe ba event (`PaymentPartiallyPaidEvent`, `PaymentTopupReminderEvent`, `PaymentForfeitedEvent`), gọi `NotificationService.createNotification(userId, type, title, content, paymentId, data)` với `data` chứa `order_code`, `amount_paid`, `amount_remaining`, `topup_deadline` để app điền sẵn. Chỉ thông báo trong app (push/email là ngoài phạm vi). Nội dung xem mục "Nội dung thông báo". |
| `common/exception/ErrorCode.java` | Thêm mã cần thiết (VD `PAYMENT_NOT_PARTIAL`). |
| `module/user/service/impl/UserServiceImpl.java` | Không cần đổi; hạn premium tính từ lúc đơn `PAID` như hiện tại. |

**Test** (dưới `src/test/java/com/datn/financeapp/`): `unitTest/pay/service/impl/PaymentPartialPaymentTest.java`, `unitTest/pay/scheduler/PaymentTopupExpiryJobTest.java`, `unitTest/pay/scheduler/PaymentSyncJobTest.java`, `integrationTest/pay/controller/PaymentTopupIntegrationTest.java`, `integrationTest/pay/repository/PaymentLinkRepositoryTest.java`, mở rộng `FakePayOsClient`/`PaymentInvariantFuzzIntegrationTest` của Giai đoạn 1; sửa các test hiện có bị ảnh hưởng bởi `payment_links`.

**Tài liệu / Bruno**: `docs/api/payment.md` (luồng thiếu tiền, điều khoản giữ tiền), `bruno/15-Payments/User/04-Create-Topup-Link.yml`.

**Không đổi**: `PayOsClientHelper.java` (chỉ gọi thêm `createLink` với số tiền còn thiếu).

### G2.2 Lý do thay đổi

- Hiện không có chỗ lưu "đã nhận bao nhiêu": `Payment` chỉ có `amount` (yêu cầu), nên khoản thiếu hoặc bị bỏ qua (`markPaid` lệch số tiền chỉ log) hoặc làm đơn kẹt (`#14`).
- Mỗi link payOS có `orderCode` riêng; webhook của link bù không tìm được đơn nếu chỉ lưu một `order_code` trên `payments` → cần `payment_links`.
- Rule 2.3 (enum lưu `@Enumerated(EnumType.STRING)`), 2.9 (mọi truy vấn của user lọc theo `userId`), 2.12 (lỗi qua `BusinessException`).

### G2.3 Hướng giải quyết / code xem trước

**Mô hình dữ liệu (⚠️ đổi schema `V19`; cột `payments.amount_paid` đã có từ `V17` của Giai đoạn 1)**

```sql
CREATE TABLE payment_links (
    id            UUID PRIMARY KEY,
    payment_id    UUID NOT NULL REFERENCES payments(id),
    order_code    BIGINT NOT NULL UNIQUE,      -- mã gửi payOS cho riêng link này
    payos_link_id VARCHAR(100),
    amount        BIGINT NOT NULL CHECK (amount > 0),
    amount_paid   BIGINT NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL,        -- PENDING, PAID, CANCELLED, EXPIRED
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at/updated_at/is_deleted ...
);
ALTER TABLE payments ADD COLUMN topup_deadline TIMESTAMPTZ, ADD COLUMN topup_reminded_at TIMESTAMPTZ,
                     ADD COLUMN forfeited_at TIMESTAMPTZ;
```

- `payments.order_code` giữ làm mã công khai của đơn (API dùng như cũ); link đầu tiên dùng đúng `order_code` đó.

**Nội dung thông báo** (tiếng Việt, số tiền định dạng `49.000đ`, giờ theo `Asia/Ho_Chi_Minh` dạng `HH:mm dd/MM/yyyy`; câu chữ có thể chỉnh):

| Loại | Tiêu đề | Nội dung |
|---|---|---|
| `PAYMENT_TOPUP_REQUIRED` | Thanh toán chưa đủ | Đơn #{order_code} mới nhận {amount_paid} trên tổng {amount}. Vui lòng thanh toán nốt {amount_remaining} trước {topup_deadline}. Quá hạn, số tiền đã nhận sẽ không được hoàn lại. |
| `PAYMENT_TOPUP_REMINDER` | Sắp hết hạn bù tiền | Còn khoảng {minutes_left} phút để thanh toán nốt {amount_remaining} cho đơn #{order_code}. Quá hạn, số tiền đã nhận ({amount_paid}) sẽ không được hoàn lại. |
| `PAYMENT_FORFEITED` | Đơn thanh toán đã đóng | Đơn #{order_code} đã quá hạn bù tiền nên không được kích hoạt, số tiền đã nhận ({amount_paid}) không được hoàn lại. Cần hỗ trợ, vui lòng liên hệ chúng tôi và cung cấp mã đơn. |

**Luồng**

1. Webhook **hoặc** `PaymentSyncJob` (mỗi 1 phút, chỉ đơn `PENDING`/`PARTIALLY_PAID` còn trong hạn: `getLink`) → `received` của link theo `receivedOf` (#14, đặt trực tiếp theo tổng giao dịch nên không cần payOS gửi webhook cho khoản thiếu, cũng không cần biết payOS cộng dồn hay không) → cập nhật `amount_paid` của link và của đơn (idempotent theo link, khoá dòng).
2. `amount_paid(đơn) >= amount` → `PAID` + `PaymentPaidEvent` (như cũ).
3. `0 < amount_paid < amount` → `PARTIALLY_PAID`, đặt `topup_deadline` một lần (lần thiếu đầu tiên), huỷ link hiện tại nếu còn mở theo bất biến #13, phát `PaymentPartiallyPaidEvent` (thông báo còn thiếu `amount - amount_paid`, hạn bù).
4. User gọi `POST /payments/{order_code}/topup-links` → server tạo link mới với số tiền còn thiếu (kèm huỷ link bù cũ nếu còn) và trả `topup_checkout_url`.
5. Job `PaymentTopupExpiryJob` (chu kỳ `PAYMENT_TOPUP_JOB_SECONDS`): còn `<= PAYMENT_TOPUP_REMINDER_SECONDS` tới hạn và chưa nhắc → phát `PaymentTopupReminderEvent` một lần; `PARTIALLY_PAID` quá `topup_deadline` → huỷ link bù còn mở (xác nhận đã chết), ghi `FORFEITED`, `forfeited_at`, phát `PaymentForfeitedEvent`, log `[PAYMENT-RECONCILE]` kèm `amount_paid`.

**⚠️ API contract (ảnh hưởng app Flutter)**

| Thay đổi | Ảnh hưởng FE |
|---|---|
| `status` có thêm `PARTIALLY_PAID`, `FORFEITED` | FE xử lý đủ 6 trạng thái. |
| Mới các trường `amount_paid`, `amount_remaining`, `topup_deadline`, `topup_checkout_url` | Hiển thị "còn thiếu X, bù trước hh:mm". |
| Mới `POST /payments/{order_code}/topup-links` | Nút "Thanh toán phần còn thiếu". |
| Điều khoản trước khi thanh toán | FE phải hiển thị "chuyển thiếu quá hạn bù sẽ không được hoàn" trước khi mở `checkout_url`. |
| Thông báo mới `PAYMENT_TOPUP_REQUIRED`, `PAYMENT_TOPUP_REMINDER`, `PAYMENT_FORFEITED` | App chọn icon/màn hình đích cho ba loại mới. |

**Test viết đỏ trước** (đỏ vì khẳng định sai ở):
- trả 3.000/10.000 → đơn `PARTIALLY_PAID`, `amount_paid = 3000`, **không** giao hàng, có `topup_deadline` (hiện code log `ERROR` và giữ `PENDING`);
- bù 7.000 trong hạn → `PAID`, giao hàng đúng một lần, premium tính từ lúc đủ tiền;
- bù lẻ nhiều lần (3.000 + 3.000 + 4.000) → chỉ giao ở lần đủ;
- webhook gửi lại một khoản đã cộng → không cộng hai lần;
- quá hạn bù → `FORFEITED`, không giao, thông báo đã gửi, user tạo được đơn mới sau đó;
- race: bù tiền đúng lúc job `FORFEITED` chạy → hoặc `PAID` hoặc `FORFEITED`, không bao giờ `FORFEITED` mà có đủ tiền; chứng minh bằng `FakePayOsClient` + fuzz mở rộng;
- user có đơn `PARTIALLY_PAID` bấm nâng cấp → bị chặn bằng lỗi rõ ràng (quyết định #5);
- cấu hình: thiếu `PAYMENT_TOPUP_SECONDS` hoặc `<= 0` → app không khởi động;
- nhắc hạn: đơn `PARTIALLY_PAID` còn đúng `PAYMENT_TOPUP_REMINDER_SECONDS` tới hạn → một thông báo nhắc, job chạy lại không nhắc lần hai (`topup_reminded_at`); `PAYMENT_TOPUP_SECONDS <= PAYMENT_TOPUP_REMINDER_SECONDS` → không nhắc; đơn đã `PAID` trước mốc nhắc → không nhắc;
- phần thiếu nhỏ hơn 2000 (VD đơn 10.000, đã nhận 9.000 → thiếu 1.000) → link bù tạo ra với số tiền 2.000, tổng nhận 11.000 thì `PAID` và theo #20 phần dư 1.000 không hoàn;
- chu kỳ job: với `PAYMENT_TOPUP_SECONDS = 30`, `PAYMENT_TOPUP_JOB_SECONDS = 10`, đơn quá hạn bù được đóng sau không quá một chu kỳ job (dùng `MutableClock`).

### G2.4 Danh sách công việc chi tiết

1. Thử thật với payOS hai điều còn lại ở câu hỏi G2-A (chặn việc code).
2. Viết `V19` và chuyển dữ liệu `payment_links`.
3. Viết test đỏ (unit) rồi sửa `PaymentServiceImpl`, entity, repository.
4. Event + listener thông báo, `NotiType`.
5. `PaymentTopupExpiryJob` (đóng đơn quá hạn + nhắc hạn), cấu hình `PAYMENT_TOPUP_SECONDS`, `PAYMENT_TOPUP_REMINDER_SECONDS`, `PAYMENT_TOPUP_JOB_SECONDS` trong `.env.example` (mẫu ghi giá trị test `30`/`3600`/`10`).
6. API `topup-links`, DTO, mapper.
7. Mở rộng `FakePayOsClient`, test bất biến/race/fuzz, integration test.
8. Tài liệu, Bruno, cập nhật Giai đoạn 1 (đánh dấu quyết định #14 đã bị thay thế).
9. Biên dịch, `git diff` đối chiếu mục 1, báo cáo.

### Ngoài phạm vi (Giai đoạn 2)

- Hoàn tiền (theo quyết định: giữ tiền khi quá hạn bù); thông báo push/email ngoài app.
- Trả thừa tiền: đã xử lý ở Giai đoạn 1 (quyết định #20). Khi cộng dồn các khoản bù mà tổng `>=` số đơn thì vẫn `PAID`, phần dư theo #20.
- Cho admin điều chỉnh/hoàn thủ công một đơn `FORFEITED`.

### Kiểm tra (Giai đoạn 2)

- Hỏi bạn trước khi chạy test; không chạy full test.

```powershell
mvn test -Dtest=PaymentPartialPaymentTest,PaymentTopupExpiryJobTest,PaymentServiceImplTest
```

```powershell
mvn test -Dtest=PaymentTopupIntegrationTest,PaymentLinkRepositoryTest,PaymentInvariantFuzzIntegrationTest,PaymentWebhookIntegrationTest
```

- `rg -n "UNDERPAID|PROCESSING|FAILED" src/main/java` để chắc không còn xét từng trạng thái ngoài `isDead`/`closeUnpaid` (#14).
- Bạn tự chạy full test nếu cần kết quả tổng.

### Câu hỏi còn mở (Giai đoạn 2)

| # | Câu hỏi | Đề xuất |
|---|---|---|
| G2-A | **Chỉ còn 2 điều phải thử thật với payOS** (hai điều còn lại đã né được bằng thiết kế, xem #14): (2) sau khi link đã nhận một phần (VD 3.000/10.000), user có chuyển tiếp vào đúng QR cũ được không (nếu được thì không cần link bù mới, Giai đoạn 2 đơn giản hơn nhiều); (3) `cancelLink` trên link đã nhận một phần có được không (nếu không được thì user vẫn chuyển được tiền vào sau khi ta đóng đơn, đúng loại lỗi bất biến #13 chặn). Hai điều đã né: (1) payOS có gửi webhook cho khoản thiếu không → không cần biết vì `PaymentSyncJob` tự hỏi payOS; (4) chuyển 3.000 rồi 2.000 payOS báo gộp hay lẻ → không cần biết vì `receivedOf` lấy số lớn hơn giữa tổng các giao dịch và trường tổng hợp. | **Cách thử (~15 phút, vài nghìn đồng, khoá payOS dev):** tạo link 10.000đ → chuyển 3.000đ → ghi lại `getLink` (status, `amountPaid`, `transactions`) → quét lại đúng QR chuyển thêm 2.000đ → ghi lại `getLink` → gọi `cancelLink` → ghi lại kết quả và `getLink` sau đó; xem webhook nào về. Dán kết quả vào đây để chốt thiết kế, hoặc bạn cho tôi khoá test để tự chạy. Thêm quan sát: link ở trạng thái `FAILED` nghĩa là gì (ghi chú #14). |

### Bảng công việc (Giai đoạn 2)

| # | Công việc | Trạng thái |
|---|---|---|
| 1 | Xác minh payOS (câu G2-A) | Chưa làm |
| 2 | `V19` + chuyển dữ liệu | Chưa làm |
| 3 | Test đỏ + sửa service/entity/repository | Chưa làm |
| 4 | Event, listener, `NotiType` | Chưa làm |
| 5 | Job (đóng đơn + nhắc hạn) + cấu hình `PAYMENT_TOPUP_SECONDS`/`..._REMINDER_SECONDS`/`..._JOB_SECONDS` | Chưa làm |
| 6 | API `topup-links`, DTO, mapper | Chưa làm |
| 7 | `FakePayOsClient`, test bất biến/race/fuzz, integration | Chưa làm |
| 8 | Tài liệu, Bruno, sửa Giai đoạn 1 | Chưa làm |
| 9 | Biên dịch, đối chiếu diff, báo cáo | Chưa làm |
