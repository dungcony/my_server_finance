---
phase: 01-nen-tang-xac-thuc
plan: 06
subsystem: infra
tags: [testrestemplate, testcontainers, roadmap, flaky-test, end-to-end]

# Dependency graph
requires:
  - phase: 01-01
    provides: "Dự án Maven Spring Boot chạy được, migration V1-V7 verify sạch"
  - phase: 01-02
    provides: "common/response, common/exception, common/security (JwtService/JwtAuthFilter/SecurityConfig)"
  - phase: 01-03
    provides: "common/idempotency (@Idempotent/IdempotencyAspect), common/ratelimit (RateLimitFilter 3 tầng)"
  - phase: 01-04
    provides: "AuthController 4 endpoint lõi (register/login/refresh/logout)"
  - phase: 01-05
    provides: "AuthController 5 endpoint còn lại (me/update-profile/change-password/forgot-password/reset-password) — đủ 9/9"
provides:
  - "ROADMAP.md Phase 1 khớp đúng thực tế đã triển khai (D-08) — không còn mô tả sai 'viết V6/V7 trước khi viết code Java', đánh dấu 6/6 plan hoàn thành, Complete"
  - "AuthIdempotencyRateLimitEndToEndTest — bằng chứng sống qua HTTP thật rằng JwtAuthFilter -> RateLimitFilter -> IdempotencyAspect -> AuthController phối hợp đúng cùng nhau, không riêng lẻ từng mảnh"
  - "README.md mục 'Trạng thái Phase 1' xác nhận 9/9 endpoint, hạ tầng idempotency/rate limit sẵn sàng cho Phase 2"
  - "JwtServiceTest.tamperedSignature_throwsSignatureException hết flaky — mutation signature bằng đảo bit thay vì đổi ký tự cuối chuỗi base64url"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Test end-to-end xuyên nhiều lớp hạ tầng (filter + AOP + controller) phải dùng TestRestTemplate + webEnvironment=RANDOM_PORT thay vì MockMvc — MockMvc không đi qua Servlet Filter chain thật theo đúng nghĩa network round-trip, và các test tích hợp trước đó (Plan 04/05) đều chủ động thay RateLimitFilter bằng no-op để tránh nhiễu; test Plan 06 phải để nguyên filter thật vì đó chính là điều cần verify"
    - "RateLimitFilter dùng Caffeine cache bucket theo key IP, bean singleton sống xuyên suốt ApplicationContext test — nhiều @Test method trong cùng class chia sẻ chung 1 bucket nếu cùng nhóm quota; viết behavior liên quan cùng 1 method theo đúng thứ tự tiêu thụ quota để tránh rò rỉ trạng thái chéo giữa test case"
    - "Mutation test cho chữ ký JWT phải thao tác trên mảng byte đã giải mã (Base64.getUrlDecoder), không thao tác trực tiếp trên ký tự cuối chuỗi base64url — ký tự cuối có thể là phần đệm không ảnh hưởng giá trị giải mã, khiến mutation là no-op ngẫu nhiên"

key-files:
  created:
    - src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java
  modified:
    - .planning/ROADMAP.md
    - README.md
    - src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java
    - .planning/phases/01-nen-tang-xac-thuc/deferred-items.md

key-decisions:
  - "Verify CORE-05/06/07/08 bằng grep thật (không chỉ đọc code bằng mắt) — xác nhận đúng như CONTEXT.md dự đoán: CORE-05/06/08 chưa có bề mặt kiểm chứng ở Phase 1 (không endpoint multi-user, không DELETE, không trường amount), CORE-07 đã thoả mãn đầy đủ (toàn bộ timestamp entity dùng Instant khớp TIMESTAMPTZ)"
  - "Test end-to-end viết 2 method thay vì 1 — method đầu verify đủ 3 behavior theo đúng thứ tự tiêu thụ quota (5 request thành công + header, request thứ 6 429), method sau verify riêng D-11 bằng 2 email cùng Idempotency-Key, chấp nhận kết quả phụ thuộc trạng thái quota còn lại từ method trước (201 hoặc 429 đều không phải hành vi cache-response — chỉ cache mới là vi phạm D-11)"
  - "Sửa flaky JwtServiceTest theo đúng đề xuất user: đảo bit byte GIỮA mảng signature đã giải mã base64url thay vì đổi ký tự cuối chuỗi mã hoá — loại bỏ hoàn toàn khả năng mutation rơi vào padding"

requirements-completed: [CORE-01, CORE-02, CORE-03, CORE-04, CORE-05, CORE-06, CORE-07, CORE-08, CORE-09, CORE-10, CORE-11]

# Metrics
duration: 24min
completed: 2026-08-23
---

# Phase 1 Plan 06: Đóng phase — verify toàn bộ suite, ROADMAP khớp thực tế, test end-to-end Summary

**ROADMAP.md Phase 1 cập nhật khớp đúng thực tế (D-08, 6/6 plan Complete), test end-to-end `AuthIdempotencyRateLimitEndToEndTest` qua HTTP thật xác nhận JwtAuthFilter/RateLimitFilter/IdempotencyAspect/AuthController phối hợp đúng cùng nhau (không riêng lẻ), và fix bổ sung flaky `JwtServiceTest` bằng mutation an toàn ở byte giữa signature đã giải mã.**

## Performance

- **Duration:** 24 phút
- **Started:** 2026-08-23T01:52:00Z (ước lượng từ session start)
- **Completed:** 2026-08-23T02:03:52Z
- **Tasks:** 2/2 hoàn thành theo plan + 1 task bổ sung được user duyệt trước (fix flaky test)
- **Files modified:** 1 file mới, 4 file sửa

## Accomplishments

- Chạy `mvn test` toàn bộ suite (không filter) trước khi sửa gì — xác nhận baseline đã xanh 32/32 test (không có xung đột ẩn giữa 5 Plan trước)
- Verify CORE-05/06/07/08 bằng 4 lệnh `grep` thật, ghi kết quả cụ thể vào SUMMARY này (không chỉ liệt kê ID suông):
  - `grep -n "currentUserId" AuthController.java` → 3 dòng khớp (`me`, `updateProfile`, `changePassword`), không có endpoint nào nhận `userId` từ client — **CORE-05 chưa có bề mặt kiểm chứng ở Phase 1**, sẽ áp dụng đầy đủ từ Phase 2
  - `grep -rn "@DeleteMapping" auth/` → rỗng — **CORE-06 chưa có bề mặt kiểm chứng ở Phase 1** (không có endpoint DELETE nào trong 9 endpoint auth)
  - `grep -rn "Instant" auth/entity/*.java IdempotencyKeyEntity.java` → 14 dòng khớp, toàn bộ cột `TIMESTAMPTZ` của 4 bảng hạ tầng V7 map đúng `Instant`, không có `LocalDateTime` nào — **CORE-07 đã hoàn thành đầy đủ ngay từ Phase 1**
  - `grep -rn "amount\|BigDecimal\|double \|float " auth/` → rỗng — **CORE-08 chưa có bề mặt kiểm chứng ở Phase 1** (không trường `amount` nào trong DTO/entity auth)
- Sửa `ROADMAP.md` mục Phase 1: đổi mô tả "viết migration trước khi viết code Java" thành "verify migration đã có, không viết mới" (D-06/D-07/D-08); đánh dấu 6/6 plan `[x]` với mô tả ngắn từng plan; bảng Progress đổi `5/6 In progress` thành `6/6 Complete`, ngày hoàn thành `2026-08-23`
- Viết `AuthIdempotencyRateLimitEndToEndTest` — 2 test method dùng `TestRestTemplate` + `webEnvironment=RANDOM_PORT` + Testcontainers, **KHÔNG mock/no-op `RateLimitFilter`** như mọi test tích hợp trước đó (Plan 04/05 chủ động thay filter này bằng no-op để tránh nhiễu quota — Plan 06 để nguyên vì đây chính là điều cần verify):
  - Method 1: gửi 5 request `/auth/register` liên tiếp (email khác nhau) → cả 5 thành công `201`, header `X-RateLimit-Limit=5` xuất hiện; request thứ 6 → `429 RATE_LIMIT_EXCEEDED`, `X-RateLimit-Remaining=0` (T-06-02)
  - Method 2: 2 request `/auth/register` cùng `Idempotency-Key`, email khác nhau → nếu cả 2 thành công thì `user_id` phải khác nhau tuyệt đối (T-06-01/D-11) — chứng minh sống động rằng nếu ai đó vô tình gắn `@Idempotent` lên endpoint này sau này, test sẽ đỏ ngay
  - Chạy lại 4 lần liên tiếp (`mvn test -Dtest=AuthIdempotencyRateLimitEndToEndTest`) đều pass — không flaky do phụ thuộc thứ tự bucket rate limit
- Cập nhật `README.md` — thêm mục "Trạng thái Phase 1" xác nhận 9/9 endpoint auth, tóm tắt hạ tầng idempotency/rate limit dùng lại cho Phase 2, cách chạy `mvn test`, và dòng dẫn tới `ROADMAP.md` Phase 2
- **[Task bổ sung được user duyệt trước]** Sửa `JwtServiceTest.tamperedSignature_throwsSignatureException` hết flaky: thay vì đổi ký tự cuối cùng chuỗi base64url (có xác suất nhỏ rơi vào padding, không đổi giá trị giải mã), giải mã signature segment thành mảng byte thật rồi đảo bit (`^= 0xFF`) của byte **ở giữa** mảng, mã hoá lại — không thể là no-op bất kể vị trí padding. Verify: 10 lần chạy liên tục `mvn test -Dtest=JwtServiceTest` riêng lẻ đều pass, `mvn test` toàn bộ suite (34 test) cũng pass. Cập nhật `deferred-items.md` đánh dấu `[ĐÃ SỬA]`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Cập nhật ROADMAP.md theo D-08 + verify CORE-05/06/07/08 + chạy baseline test suite** - `1b3f40d` (docs)
2. **Task 2: Test end-to-end Idempotency + Rate limit + Auth qua HTTP thật + README** - `8587c26` (test)
3. **[User-approved, ngoài phạm vi plan]: Fix JwtServiceTest flaky** - `c720aa1` (fix)

**Plan metadata:** (commit theo sau khi ghi SUMMARY.md)

## Files Created/Modified

- `.planning/ROADMAP.md` — mô tả Phase 1 khớp thực tế D-08, danh sách 6 plan đánh dấu hoàn thành, bảng Progress `6/6 Complete`
- `src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java` — test end-to-end HTTP thật, không mock filter nào
- `README.md` — thêm mục "Trạng thái Phase 1"
- `src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java` — sửa mutation signature test hết flaky
- `.planning/phases/01-nen-tang-xac-thuc/deferred-items.md` — đánh dấu mục flaky test đã sửa

## Decisions Made

- 4 lệnh `grep` xác nhận CORE-05/06/07/08 chạy thật và ghi kết quả cụ thể vào SUMMARY (không chỉ đọc bằng mắt) — đúng yêu cầu `must_haves` của plan, xác nhận 3/4 mã (05/06/08) chưa có bề mặt kiểm chứng ở Phase 1 (không phải bỏ sót, sẽ implement đầy đủ khi Phase 2 có tài nguyên multi-user/DELETE/amount tương ứng), còn CORE-07 đã hoàn thành ngay từ Phase 1
- Test end-to-end viết 2 `@Test` method (không phải 1 method chứa mọi behavior như PLAN gợi ý ban đầu) — tách riêng phần verify D-11 (Idempotency-Key bị bỏ qua) vì cần logic chấp nhận cả 2 trạng thái quota hợp lệ (201 hoặc 429 tuỳ bucket còn lại), giữ method chính (rate limit 6 request) làm nguồn sự thật duy nhất cho thứ tự tiêu thụ quota — tránh 1 method quá dài, khó đọc
- Sửa `JwtServiceTest` đúng theo hướng dẫn user: đảo bit byte GIỮA mảng signature đã giải mã, không đổi ký tự cuối chuỗi mã hoá — verify bằng 10 lần chạy liên tục + full suite

## Deviations from Plan

### Auto-fixed Issues

Không có deviation nào theo Rule 1-3 — cả 2 task chạy đúng như PLAN.md mô tả ngay từ lần thử đầu tiên (baseline `mvn test` đã xanh sẵn từ Plan 05, không phát hiện xung đột ẩn nào giữa 5 plan trước).

### Task bổ sung được user duyệt trước khi thực thi

**1. Fix `JwtServiceTest.tamperedSignature_throwsSignatureException` flaky (ghi trong `deferred-items.md` từ Plan 04)**
- **Phạm vi:** Nằm ngoài `<tasks>` của `01-06-PLAN.md`, được user phê duyệt tường minh trong prompt trước khi thực thi (không phải Rule 1-3 tự động, không phải Rule 4 cần hỏi — đã hỏi và được duyệt từ trước)
- **Fix:** Đổi cơ chế mutation từ "đổi ký tự cuối chuỗi base64url" sang "giải mã byte thật, đảo bit byte giữa mảng, mã hoá lại"
- **Files modified:** `src/test/java/com/datn/financeapp/common/security/JwtServiceTest.java`, `.planning/phases/01-nen-tang-xac-thuc/deferred-items.md`
- **Verification:** 10 lần chạy liên tục riêng lẻ + 1 lần full suite, tất cả pass
- **Commit:** `c720aa1`

---

**Total deviations:** 0 auto-fixed, 1 task bổ sung được duyệt trước
**Impact on plan:** Không ảnh hưởng phạm vi hay kiến trúc plan gốc — cả 2 task chính thực thi đúng như mô tả.

## Issues Encountered

Không có blocker nào. Docker daemon chạy sẵn, Testcontainers ổn định xuyên suốt toàn bộ lần chạy test (baseline, test end-to-end mới, 10 lần chạy JwtServiceTest, và full suite cuối cùng).

## User Setup Required

Không có bước thủ công bên ngoài.

## Next Phase Readiness

- **Phase 1 chính thức đóng: 6/6 plan hoàn thành**, `mvn test` toàn bộ suite (34 test) xanh khi chạy liên tục 1 lần không filter
- 9/9 endpoint `api/01-XAC-THUC.md` hoạt động qua HTTP thật, hạ tầng `common/idempotency` + `common/ratelimit` + `common/response` + `common/security` sẵn sàng dùng lại nguyên vẹn cho Phase 2 (Ví & Danh mục) không cần sửa gì
- `ROADMAP.md` không còn mô tả sai lệch với thực tế đã triển khai — Phase 2 có thể bắt đầu lập kế hoạch chi tiết dựa trên tài liệu chính xác
- Không có blocker nào cho Phase 2

---
*Phase: 01-nen-tang-xac-thuc*
*Completed: 2026-08-23*

## Self-Check: PASSED

File `src/test/java/com/datn/financeapp/auth/AuthIdempotencyRateLimitEndToEndTest.java` xác nhận tồn tại trên đĩa; commit `1b3f40d`, `8587c26`, `c720aa1` xác nhận có trong `git log`. `mvn test` toàn bộ project (34 test) — BUILD SUCCESS, 0 failures, 0 errors, chạy liên tục không filter.
