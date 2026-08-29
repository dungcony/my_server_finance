---
phase: 06-n-i-api-th-t-v-i-app-flutter
plan: 01
subsystem: api
tags: [spring-boot, context-path, rate-limit, jwt, budget, seed-data]

# Dependency graph
requires: []
provides:
  - "server.servlet.context-path: /v1 hoạt động đúng cho toàn bộ backend"
  - "Rate-limit auth/ai giữ đúng quota 5/phút, 30/phút sau khi thêm context-path"
  - "401 phân biệt TOKEN_EXPIRED/TOKEN_INVALID/UNAUTHENTICATED theo nguyên nhân thật"
  - "affected_budgets thật trong POST/PUT/duplicate transactions (không còn luôn rỗng)"
  - "DevDataSeeder idempotent (@Profile(dev)) cho dữ liệu thử Phase 6"
affects: [06-02, 06-03, 06-04, 06-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "computeAffectedBudgets() gọi đồng bộ trong cùng @Transactional của TransactionService, khác BudgetAlertListener chạy AFTER_COMMIT bất đồng bộ"
    - "DevDataSeeder dùng ON CONFLICT DO NOTHING cho bảng có UNIQUE (users, wallets), SELECT-before-INSERT cho bảng không có UNIQUE (budgets)"

key-files:
  created:
    - src/main/java/com/datn/financeapp/transaction/dto/AffectedBudgetResponse.java
    - src/main/java/com/datn/financeapp/common/seed/DevDataSeeder.java
    - src/test/java/com/datn/financeapp/transaction/TransactionAffectedBudgetsIntegrationTest.java
  modified:
    - src/main/resources/application.yml
    - src/main/java/com/datn/financeapp/common/ratelimit/RateLimitProperties.java
    - src/main/java/com/datn/financeapp/common/exception/GlobalExceptionHandler.java
    - src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionResponse.java
    - src/main/java/com/datn/financeapp/transaction/service/TransactionService.java
    - src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java
    - src/test/java/com/datn/financeapp/common/ratelimit/RateLimitFilterTest.java
    - .planning/STATE.md

key-decisions:
  - "pathPrefix của RateLimitProperties gắn cứng /v1/auth/, /v1/ai/ thay vì đọc động qua @Value — context-path đã chốt D-05, không đổi trong dự án này"
  - "computeAffectedBudgets chỉ tính khi type=expense và categoryId != null, cùng điều kiện BudgetAlertListener đã áp dụng"
  - "DevDataSeeder chỉ @Profile(dev), mật khẩu seed cố định SeedPass123! chấp nhận được vì không bao giờ bật ở prod (T-06-02, accept)"

patterns-established:
  - "Xoá constructor rút gọn của DTO khi trường đó PHẢI được tính thật ở mọi call site — buộc compiler bắt lỗi quên truyền thay vì lặng lẽ trả rỗng"

requirements-completed: []

# Metrics
duration: ~35min
completed: 2026-08-29
---

# Phase 6 Plan 1: Nền tảng /v1 + vá 401 + affected_budgets + seed data Summary

**Thêm context-path /v1 kèm vá rate-limit/test đồng bộ, sửa lỗi 401 luôn trả UNAUTHENTICATED, điền affected_budgets thật cho POST/PUT/duplicate transactions, và dựng DevDataSeeder idempotent — nền tảng bắt buộc trước khi app Flutter nối API thật.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-08-29T04:04:50Z (theo STATE.md, phiên "Phase 6 context gathered")
- **Completed:** 2026-08-29T04:13:08Z
- **Tasks:** 4 (Task 3 chạy theo TDD: RED → GREEN)
- **Files modified:** 11 (8 sửa, 3 mới)

## Accomplishments

- Backend chạy trên `context-path: /v1`, đồng thời vá 2 lỗ hổng âm thầm mà thay đổi này kéo theo: rate-limit `auth`/`ai` không rơi về rule `default` 120/phút, và test end-to-end `TestRestTemplate` không tự cộng context-path
- 401 do access token hết hạn/sai chữ ký giờ trả đúng `error.code = TOKEN_EXPIRED`/`TOKEN_INVALID` thay vì luôn hardcode `UNAUTHENTICATED` — `AuthInterceptor` phía app giờ có thể phân biệt được để tự làm mới token
- `POST /transactions` (và PUT/duplicate) trả `affected_budgets` thật: cộng gộp danh mục con đúng (chi vào "Cà phê" kích hoạt ngân sách đặt ở "Ăn uống"), loại bỏ ngân sách `status=normal`, income luôn trả rỗng
- `DevDataSeeder` mới (`@Profile("dev")`) dựng user/2 ví/1 ngân sách thử, chạy lại nhiều lần không tạo trùng bản ghi

## Task Commits

1. **Task 1: Thêm context-path /v1, vá 2 điểm vỡ âm thầm** - `8bc91b2` (feat)
2. **Task 2: Sửa lỗi 401 luôn trả UNAUTHENTICATED** - `7d9e3b1` (fix)
3. **Task 3: Điền affected_budgets thật** - `4ca274b` (test, RED) → `9b869bb` (feat, GREEN — bao gồm cả fix Rule 3 cho `RateLimitFilterTest`)
4. **Task 4: DevDataSeeder idempotent + sửa STATE.md** - `84a80df` (feat)

**Plan metadata:** (commit này, sau khi tạo SUMMARY)

_Task 3 dùng TDD: test tích hợp viết trước (RED), sau đó implement DTO/service để GREEN. Do máy phát triển không có Docker daemon chạy, RED/GREEN không tự chạy được bằng Testcontainers thật — xem "TDD Gate Compliance" bên dưới._

## Files Created/Modified

- `src/main/resources/application.yml` - thêm khối `server.servlet.context-path: /v1`
- `src/main/java/com/datn/financeapp/common/ratelimit/RateLimitProperties.java` - `pathPrefix` đổi thành `/v1/auth/`, `/v1/ai/`
- `src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java` - path literal `/v1/auth/register` (TestRestTemplate không tự cộng context-path)
- `src/main/java/com/datn/financeapp/common/exception/GlobalExceptionHandler.java` - `handleAuthentication` đọc `JwtAuthFilter.ATTR_TOKEN_ERROR`, trả đúng code/message theo nguyên nhân
- `src/main/java/com/datn/financeapp/transaction/dto/AffectedBudgetResponse.java` - DTO mới map từ `BudgetProgressProjection`
- `src/main/java/com/datn/financeapp/transaction/dto/CreateTransactionResponse.java` - `List<Object>` → `List<AffectedBudgetResponse>`, xoá constructor 2-tham số
- `src/main/java/com/datn/financeapp/transaction/service/TransactionService.java` - thêm `computeAffectedBudgets()`, nối vào `create()`/`update()`/`duplicate()`
- `src/test/java/com/datn/financeapp/transaction/TransactionAffectedBudgetsIntegrationTest.java` - 3 test tích hợp (cộng gộp danh mục con, income luôn rỗng, status normal không xuất hiện)
- `src/test/java/com/datn/financeapp/common/ratelimit/RateLimitFilterTest.java` - sửa path literal `/auth/login` → `/v1/auth/login` (Rule 3, vỡ do Task 1)
- `src/main/java/com/datn/financeapp/common/seed/DevDataSeeder.java` - seed idempotent user/2 ví/1 ngân sách
- `.planning/STATE.md` - ghi nhận DebtReminderWorker/ExportAsyncRunner đã hiện thực đầy đủ

## Decisions Made

- `pathPrefix` gắn cứng `/v1/...` thay vì đọc động — context-path đã chốt vĩnh viễn (D-04/D-05), đơn giản hơn `@Value`
- `computeAffectedBudgets` gọi đồng bộ trong transaction ghi giao dịch (khác `BudgetAlertListener` chạy `AFTER_COMMIT`), vì response phải trả ngay trong request đó
- Xoá constructor rút gọn `CreateTransactionResponse(transaction, newBalance)` — buộc mọi call site truyền `affectedBudgets` tường minh, ngăn lỗi "quên truyền nên luôn rỗng" tái diễn
- `DevDataSeeder` chấp nhận mật khẩu cố định `SeedPass123!` vì chỉ chạy ở `@Profile("dev")`, không bao giờ ở prod/CI mặc định (T-06-02)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `RateLimitFilterTest` đỏ sau khi thêm context-path /v1**
- **Found during:** Task 3 (chạy lại toàn bộ test không cần Docker sau khi hoàn thành GREEN)
- **Issue:** `RateLimitFilterTest.moreThan5RequestsPerMinuteToAuthFromSameIp_6thRequestIs429` gọi `doFilter` trực tiếp với URI literal `/auth/login` — không qua Spring context nên không tự cộng `/v1`. Sau khi `RateLimitProperties` đổi `pathPrefix` thành `/v1/auth/` ở Task 1, request `/auth/login` rơi vào rule `default` (120/phút) thay vì `auth` (5/phút), test kỳ vọng 429 ở lần thứ 6 nhưng nhận 200.
- **Fix:** Đổi URI literal trong test thành `/v1/auth/login`.
- **Files modified:** `src/test/java/com/datn/financeapp/common/ratelimit/RateLimitFilterTest.java`
- **Verification:** `mvn -o test -Dtest=RateLimitFilterTest` xanh sau khi sửa
- **Committed in:** `9b869bb` (gộp cùng commit Task 3 vì phát hiện trong lúc verify Task 3)

---

**Total deviations:** 1 auto-fixed (1 blocking — Rule 3)
**Impact on plan:** File không nằm trong `files_modified` của plan gốc nhưng là hệ quả trực tiếp của Task 1 (thay đổi `pathPrefix`), không phải scope creep. Không sửa sẽ để lại test đỏ vĩnh viễn trong repo.

## TDD Gate Compliance

Task 3 có `tdd="true"`. Gate sequence trong git log:
- RED: `4ca274b` `test(06-01): thêm test tích hợp affected_budgets cho POST /transactions`
- GREEN: `9b869bb` `feat(06-01): điền affected_budgets thật trong POST/PUT/duplicate transactions`

**Cảnh báo:** Máy phát triển hiện tại **không có Docker daemon chạy** (`docker info` báo lỗi), nên `TransactionAffectedBudgetsIntegrationTest` (dùng Testcontainers PostgreSQL) **chưa từng chạy thật** ở cả hai pha RED và GREEN — chỉ verify được bằng `mvn -o compile`/`mvn -o test-compile` sạch. Test này PHẢI được chạy khi có Docker daemon (theo đúng ghi chú trong `<verification>` mục 3 của plan: "Nếu Docker daemon sẵn sàng") trước khi coi Task 3 là kiểm chứng đầy đủ. Logic implementation đã đối chiếu kỹ với `BudgetAlertListener`/`BudgetProgressRepository` đã có test tích hợp xanh trước đó (Phase 4), nên rủi ro thấp nhưng chưa phải bằng chứng trực tiếp.

## Issues Encountered

- Docker daemon không sẵn sàng trên máy phát triển (`docker info` lỗi "error during connect") — mọi test `*IntegrationTest`/`*EndToEndTest`/`SchemaSmokeTest`/`WalletTransferConcurrencyTest` (dùng Testcontainers) không chạy được. Đã verify bằng compile sạch + toàn bộ unit test không-Testcontainers xanh (`mvn -o test -Dtest='!*IntegrationTest,!*EndToEndTest,!SchemaSmokeTest,!WalletTransferConcurrencyTest'`). Cần chạy lại bộ tích hợp khi có Docker trước khi coi Phase 6 verify đầy đủ.

## User Setup Required

None - không cần cấu hình dịch vụ ngoài. (Lưu ý: cần Docker daemon chạy để verify đầy đủ bộ test tích hợp của plan này — xem "Issues Encountered".)

## Next Phase Readiness

- Backend đã sẵn sàng ở `context-path /v1` — plan 06-02 trở đi có thể nối app Flutter mà không cần sửa base URL
- 401 phân biệt đúng nguyên nhân — `AuthInterceptor` phía app có thể kiểm chứng luồng refresh ngầm (SC5) đúng theo thiết kế
- `affected_budgets` đã trả dữ liệu thật — plan nối `POST /transactions` phía app có thể kiểm tra cảnh báo ngân sách ngay trong response, không cần gọi thêm `GET /budgets/alerts`
- `DevDataSeeder` sẵn sàng dùng cho các phép thử D-15/D-16 (integration_test Flutter dựa vào dữ liệu nền cố định)
- **Cần chạy lại `TransactionAffectedBudgetsIntegrationTest` và `AuthIdempotencyRateLimitEndToEndTest` khi có Docker daemon** trước khi đóng Phase 6 — hiện chỉ verify bằng compile sạch

---
*Phase: 06-n-i-api-th-t-v-i-app-flutter*
*Completed: 2026-08-29*

## Self-Check: PASSED

Tất cả file tạo mới và 5 commit task đã được xác nhận tồn tại (xem lệnh kiểm ở agent log).
