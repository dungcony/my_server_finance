---
phase: 01-nen-tang-xac-thuc
plan: 03
subsystem: infra
tags: [aop, idempotency, rate-limit, bucket4j, caffeine, testcontainers, spring-security]

# Dependency graph
requires:
  - phase: 01-01
    provides: "IdempotencyKeyEntity khớp bảng idempotency_keys (V7), 6 entity tối thiểu bao gồm User"
  - phase: 01-02
    provides: "BusinessException + GlobalExceptionHandler ({success,error} thống nhất), SecurityConfig STATELESS + JwtAuthFilter, SecurityContextUtil.currentUserId()"
provides:
  - "common/idempotency/ — @Idempotent annotation + IdempotencyAspect xử lý đúng luồng completed/processing/exception (D-10..D-13), sẵn sàng gắn vào POST /wallets ở Phase 2 không cần sửa gì thêm"
  - "scheduler/IdempotencyCleanupJob — @Scheduled 3h sáng giờ Asia/Ho_Chi_Minh dọn bản ghi quá 24h (D-15)"
  - "common/ratelimit/ — RateLimitFilter Bucket4j + Caffeine 3 tầng (auth/ai/default), gắn addFilterAfter(rateLimitFilter, JwtAuthFilter.class) trong SecurityConfig, header X-RateLimit-* trên mọi response (D-16..D-21)"
  - "api/00-QUY-UOC-CHUNG.md mục 6 đã bổ sung mã REQUEST_IN_PROGRESS (409) — tài liệu và code không trôi dạt (D-14)"
affects: [01-04, 01-05, 01-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Self-invocation trong @Aspect @Component bean KHÔNG đi qua Spring AOP proxy — tách các thao tác cần @Transactional (tryInsertProcessing/deleteRecord/save) ra IdempotencyTransactionHelper là bean riêng để @Transactional thật sự áp dụng"
    - "@WebMvcTest slice test phải excludeFilters CẢ 3 bean hạ tầng cross-cutting (SecurityConfig, JwtAuthFilter, RateLimitFilter) — mỗi @Component mới thêm vào common/ có nguy cơ tự bị @WebMvcTest nạp nhầm vào context slice nếu không loại tường minh"
    - "Test Testcontainers cho luồng liên quan idempotency_keys phải tạo trước user thật qua JdbcTemplate — FK fk_idem_user ràng buộc user_id phải tồn tại trong bảng users, không thể chỉ set UUID tuỳ ý"
    - "Test giả lập Authentication qua SecurityMockMvcRequestPostProcessors.authentication(...) với principal là chuỗi UUID trần — khớp đúng hành vi JwtAuthFilter thật (không dùng @WithMockUser vì principal của nó là UserDetails, không parse được bằng UUID.fromString)"
    - "RateLimitFilter ghi JSON lỗi tĩnh trực tiếp bằng res.getWriter() — điểm DUY NHẤT trong hệ thống response 429 không đi qua GlobalExceptionHandler, vì Filter chạy trước DispatcherServlet nên không thể throw exception để Controller Advice bắt"

key-files:
  created:
    - src/main/java/com/datn/financeapp/common/idempotency/Idempotent.java
    - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyAspect.java
    - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyKeyRepository.java
    - src/main/java/com/datn/financeapp/common/idempotency/IdempotencyTransactionHelper.java
    - src/main/java/com/datn/financeapp/scheduler/IdempotencyCleanupJob.java
    - src/main/java/com/datn/financeapp/common/ratelimit/RateLimitRule.java
    - src/main/java/com/datn/financeapp/common/ratelimit/RateLimitProperties.java
    - src/main/java/com/datn/financeapp/common/ratelimit/RateLimitFilter.java
    - src/test/java/com/datn/financeapp/common/idempotency/IdempotencyAspectIntegrationTest.java
    - src/test/java/com/datn/financeapp/common/ratelimit/RateLimitFilterTest.java
  modified:
    - src/main/java/com/datn/financeapp/FinanceAppApplication.java
    - src/main/java/com/datn/financeapp/common/security/SecurityConfig.java
    - src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java
    - D:\PTIT\DATN\api\00-QUY-UOC-CHUNG.md

key-decisions:
  - "Tách IdempotencyTransactionHelper thành bean riêng ngoài kế hoạch ban đầu — self-invocation gọi method @Transactional từ trong cùng @Aspect bean không đi qua proxy Spring AOP nên transaction bị bỏ qua âm thầm; phát hiện khi review code trước khi chạy test, sửa ngay bằng cách tách bean"
  - "Insert native query trong IdempotencyKeyRepository tự sinh id/created_at bằng gen_random_uuid()/now() ngay trong SQL — IdempotencyKeyEntity không có chiến lược generation tự động (theo pattern Wave 1: id set ở tầng Service), nên INSERT ... ON CONFLICT phải tự cấp giá trị cho 2 cột NOT NULL đó"
  - "Test Testcontainers cho idempotency phải insert user thật qua JdbcTemplate trước mỗi test — phát hiện khi chạy test lần đầu gặp lỗi FK fk_idem_user, vì chưa có UserRepository (thuộc Plan 04/05)"

requirements-completed: [CORE-03, CORE-04]

# Metrics
duration: 35min
completed: 2026-08-23
---

# Phase 1 Plan 03: Hạ tầng Idempotency-Key và Rate limiting 3 tầng Summary

**AOP `@Idempotent` chặn POST-tạo-mới trùng lặp qua bảng `idempotency_keys` (INSERT ... ON CONFLICT + 409 REQUEST_IN_PROGRESS khi đang xử lý), cùng Bucket4j+Caffeine rate limit 3 tầng (auth 5/phút/IP, ai 30/phút/user, default 120/phút/user) gắn ngay sau JwtAuthFilter trong SecurityConfig.**

## Performance

- **Duration:** 35 phút
- **Started:** 2026-08-22T23:33:00Z (ước lượng từ session start)
- **Completed:** 2026-08-22T23:08:42+07:00 (commit cuối cùng của code, chưa tính SUMMARY)
- **Tasks:** 2/2 hoàn thành
- **Files modified:** 10 file mới, 4 file sửa (bao gồm 1 file ở repo `D:\PTIT\DATN` — `api/00-QUY-UOC-CHUNG.md`)

## Accomplishments

- `@Idempotent` + `IdempotencyAspect` xử lý đúng 3 nhánh D-12/D-13: chưa có key → chạy nghiệp vụ thật, lưu `completed`; đã `completed` → trả cache không chạy lại; đang `processing` → 409 `REQUEST_IN_PROGRESS` ngay lập tức, không chờ
- Nghiệp vụ ném exception → xoá bản ghi `processing` ngay (không cache lỗi), verify bằng test retry sau exception chạy lại bình thường (không bị kẹt 409 vĩnh viễn)
- `IdempotencyCleanupJob` `@Scheduled` 3h sáng giờ Việt Nam dọn bản ghi quá 24h, bọc try/catch không crash scheduler pool
- Rate limit 3 tầng đúng D-16: `/auth/**` 5/phút theo IP, `/ai/**` 30/phút theo user, còn lại 120/phút theo user — Caffeine tự evict bucket sau 5 phút không hoạt động, tối đa 100.000 bucket (T-03-04)
- Header `X-RateLimit-Limit/Remaining/Reset` xuất hiện trên MỌI response (D-19), verify bằng test riêng
- 429 trả đúng khung `{success:false, error:{code:"RATE_LIMIT_EXCEEDED"}}` dù không đi qua `GlobalExceptionHandler` (filter chạy trước DispatcherServlet) — verify bằng grep chuỗi JSON tĩnh và test parse response
- Cập nhật `api/00-QUY-UOC-CHUNG.md` mục 6 (D-14) đúng 1 dòng — verify bằng `git diff` chỉ thêm 1 dòng, không sửa nội dung khác

## Task Commits

Each task was committed atomically:

1. **Task 1: Idempotency AOP (@Idempotent) + job dọn dẹp 24h** - `fbcd054` (feat)
2. **Task 2: Rate limiting 3 tầng (Bucket4j + Caffeine) + cập nhật api/00 (D-14)** - `bf4498f` (feat)
3. **Cập nhật tài liệu API mục mã lỗi dùng chung (repo `D:\PTIT\DATN`)** - `998c44c` (docs)

**Plan metadata:** (commit theo sau khi ghi SUMMARY.md)

## Files Created/Modified

- `src/main/java/com/datn/financeapp/common/idempotency/Idempotent.java` — annotation đánh dấu method controller cần bảo vệ chống ghi trùng
- `src/main/java/com/datn/financeapp/common/idempotency/IdempotencyAspect.java` — `@Around` xử lý toàn bộ luồng D-10..D-13
- `src/main/java/com/datn/financeapp/common/idempotency/IdempotencyKeyRepository.java` — native `INSERT ... ON CONFLICT DO NOTHING`, `deleteOlderThan`
- `src/main/java/com/datn/financeapp/common/idempotency/IdempotencyTransactionHelper.java` — bean riêng bọc `@Transactional` (ngoài kế hoạch, xem Deviations)
- `src/main/java/com/datn/financeapp/scheduler/IdempotencyCleanupJob.java` — job dọn 24h
- `src/main/java/com/datn/financeapp/FinanceAppApplication.java` — thêm `@EnableScheduling`
- `src/main/java/com/datn/financeapp/common/ratelimit/RateLimitRule.java` — record cấu hình 1 quy tắc rate limit
- `src/main/java/com/datn/financeapp/common/ratelimit/RateLimitProperties.java` — bảng 3 nhóm quota tĩnh D-16
- `src/main/java/com/datn/financeapp/common/ratelimit/RateLimitFilter.java` — `OncePerRequestFilter` Bucket4j + Caffeine
- `src/main/java/com/datn/financeapp/common/security/SecurityConfig.java` — thêm `addFilterAfter(rateLimitFilter, JwtAuthFilter.class)`
- `src/test/java/com/datn/financeapp/common/idempotency/IdempotencyAspectIntegrationTest.java` — Testcontainers, 4 test case đủ behavior yêu cầu
- `src/test/java/com/datn/financeapp/common/ratelimit/RateLimitFilterTest.java` — unit test thuần, 3 test case
- `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java` — thêm `RateLimitFilter` vào `excludeFilters` (sửa regression)
- `D:\PTIT\DATN\api\00-QUY-UOC-CHUNG.md` — thêm dòng `REQUEST_IN_PROGRESS` (409) vào bảng mã lỗi dùng chung mục 6

## Decisions Made

- Tách `IdempotencyTransactionHelper` thành bean riêng — self-invocation (gọi method `@Transactional` từ trong cùng instance `@Aspect @Component`) không đi qua Spring AOP proxy, khiến annotation `@Transactional` bị bỏ qua âm thầm nếu giữ nguyên như PLAN mô tả (`protected` method cùng class). Phát hiện khi review kỹ trước khi chạy test (không phải khi chạy test thất bại) — sửa chủ động trước khi build.
- Native `INSERT` trong `IdempotencyKeyRepository.tryInsertProcessing` tự sinh `id`/`created_at` bằng `gen_random_uuid()`/`now()` ngay trong câu SQL — vì `IdempotencyKeyEntity.id` không có `@GeneratedValue` (theo pattern id set ở tầng Service của Wave 1), native query phải tự cấp cho 2 cột `NOT NULL`.
- Test Testcontainers cho idempotency insert 1 user thật qua `JdbcTemplate` trước mỗi test case — bảng `idempotency_keys` có `fk_idem_user` ràng buộc `user_id` phải tồn tại trong `users`; `UserRepository` thật chưa tồn tại (thuộc Plan 04/05) nên dùng `JdbcTemplate` cho phần setup dữ liệu tiền đề, không phải nghiệp vụ.
- Test giả lập `Authentication` bằng `SecurityMockMvcRequestPostProcessors.authentication(...)` với principal là chuỗi UUID trần thay vì `@WithMockUser` — `JwtAuthFilter` thật set principal là `String userId` (không phải `UserDetails`), và `SecurityContextUtil.currentUserId()` parse trực tiếp `principal.toString()` bằng `UUID.fromString`; `@WithMockUser` sẽ tạo `UserDetails` khiến parse UUID lỗi.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Self-invocation trong `@Aspect` bean làm `@Transactional` bị bỏ qua**
- **Found during:** Task 1, viết `IdempotencyAspect.java` (phát hiện khi review code, trước khi build/test)
- **Issue:** PLAN mô tả `handleIdempotent` là method `protected @Transactional` cùng class với `around()` (method gọi nó). Spring AOP dùng proxy để áp `@Transactional`; gọi method nội bộ qua `this.handleIdempotent(...)` bỏ qua proxy hoàn toàn, khiến toàn bộ khối `tryInsertProcessing`/`deleteRecord`/`save` chạy KHÔNG có transaction — vi phạm tính atomic yêu cầu ở D-12/D-13.
- **Fix:** Tách 3 thao tác ghi DB (`tryInsertProcessing`, `deleteRecord`, `save`) sang bean riêng `IdempotencyTransactionHelper` — gọi qua bean khác đi qua proxy Spring AOP đúng cách, `@Transactional` áp dụng thật.
- **Files modified:** `IdempotencyAspect.java`, `IdempotencyTransactionHelper.java` (file mới)
- **Verification:** `mvn test -Dtest=IdempotencyAspectIntegrationTest` — 4/4 pass, bao gồm test case exception rollback đúng transaction
- **Committed in:** `fbcd054`

**2. [Rule 1 - Bug] `@WebMvcTest` của `GlobalExceptionHandlerTest` tự nạp nhầm `RateLimitFilter` mới, phá context slice test**
- **Found during:** Task 2, chạy `mvn test` toàn bộ project sau khi thêm `RateLimitFilter`
- **Issue:** `RateLimitFilter` là `@Component` mới trong `common/`. `@WebMvcTest` không lọc `@Component` theo `controllers=`, nên tự nạp `RateLimitFilter` vào context — bean này cần `RateLimitProperties` (đã có, không lỗi) nhưng bản thân việc bị nạp vào slice test vi phạm nguyên tắc cô lập, và có nguy cơ tương tự vấn đề đã gặp ở Plan 02 với `SecurityConfig`.
- **Fix:** Thêm `RateLimitFilter.class` vào `excludeFilters` của `GlobalExceptionHandlerTest`, cùng danh sách với `SecurityConfig`/`JwtAuthFilter` đã loại từ Plan 02.
- **Files modified:** `GlobalExceptionHandlerTest.java`
- **Verification:** `mvn test` toàn bộ project — 15/15 test pass (`SchemaSmokeTest`, `GlobalExceptionHandlerTest`, `IdempotencyAspectIntegrationTest`, `RateLimitFilterTest`, `JwtServiceTest`)
- **Committed in:** `bf4498f`

**3. [Rule 3 - Blocking] FK `fk_idem_user` chặn insert `idempotency_keys` với `user_id` giả không tồn tại trong `users`**
- **Found during:** Task 1, chạy `IdempotencyAspectIntegrationTest` lần đầu
- **Issue:** Test dùng UUID cố định làm `user_id` mà không tạo user thật trước — `idempotency_keys.user_id` có FK tới `users(id)`, PostgreSQL từ chối insert với `ConstraintViolationException`.
- **Fix:** Thêm `@BeforeEach` insert 1 user thật qua `JdbcTemplate` (chưa có `UserRepository` — thuộc Plan 04/05) trước mỗi test case, xoá lại sau khi test xong.
- **Files modified:** `IdempotencyAspectIntegrationTest.java`
- **Verification:** `mvn test -Dtest=IdempotencyAspectIntegrationTest` — 4/4 pass
- **Committed in:** `fbcd054`

---

**Total deviations:** 3 auto-fixed (2 bug, 1 blocking)
**Impact on plan:** Cả 3 phát sinh khi chạy/viết test thật (2 phát hiện qua review code chủ động, 1 phát hiện qua chạy test thất bại), đúng tinh thần D-07/D-23 "verify bằng cách chạy thật". Không có scope creep, không đổi kiến trúc `common/idempotency`/`common/ratelimit` đã chốt trong PLAN.md — chỉ thêm 1 bean phụ trợ (`IdempotencyTransactionHelper`) để giữ đúng hành vi transaction PLAN yêu cầu.

## Issues Encountered

Không có vấn đề nào ngoài 3 deviation đã liệt kê ở trên — toàn bộ đã giải quyết ngay trong quá trình thực thi, không có blocker còn tồn đọng.

## User Setup Required

Không có bước thủ công bên ngoài. Docker daemon đã chạy sẵn, Testcontainers tự kéo `postgres:16` khi chạy test.

## Next Phase Readiness

- `common/idempotency/` hoàn chỉnh — Phase 2 (`POST /wallets`) chỉ cần gắn `@Idempotent` lên method controller, không cần dựng lại gì
- `common/ratelimit/` hoàn chỉnh — áp dụng ngay cho `/auth/**` ở Plan 04/05 mà không cần mở lại filter chain
- `api/00-QUY-UOC-CHUNG.md` đã đồng bộ với code — không còn khoảng trống tài liệu cho `REQUEST_IN_PROGRESS`
- Không có blocker nào cho Plan 04

---
*Phase: 01-nen-tang-xac-thuc*
*Completed: 2026-08-23*

## Self-Check: PASSED

Toàn bộ 13 file trong `key-files.created`/`key-files.modified` (bao gồm `api/00-QUY-UOC-CHUNG.md` ở repo `D:\PTIT\DATN`) xác nhận tồn tại trên đĩa; commit `fbcd054`, `bf4498f` (repo `source/server`) và `998c44c` (repo `D:\PTIT\DATN`) xác nhận có trong `git log` của repo tương ứng.
