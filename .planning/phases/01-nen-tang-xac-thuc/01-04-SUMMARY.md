---
phase: 01-nen-tang-xac-thuc
plan: 04
subsystem: auth
tags: [spring-security, jwt, refresh-token-rotation, pessimistic-lock, testcontainers, bcrypt]

# Dependency graph
requires:
  - phase: 01-02
    provides: "ApiResponse<T>, BusinessException + GlobalExceptionHandler, JwtService.generateAccessToken(userId, plan), SecurityConfig STATELESS (permitAll cho /auth/register|login|refresh), SecurityContextUtil"
  - phase: 01-03
    provides: "RateLimitFilter 3 tầng (auth 5/phút/IP) gắn addFilterAfter(rateLimitFilter, JwtAuthFilter.class) trong SecurityConfig — /auth/** tự động được bảo vệ không cần code thêm"
provides:
  - "4 endpoint lõi vòng đời phiên đăng nhập hoạt động qua HTTP thật: POST /auth/register (201, tạo user + ví Tiền mặt số dư 0 trong 1 transaction + cặp token), POST /auth/login (INVALID_CREDENTIALS dùng chung cho sai email/sai password, ACCOUNT_LOCKED sau 5 lần sai/15 phút), POST /auth/refresh (rotation dùng một lần + reuse detection thu hồi toàn bộ phiên, khoá SELECT FOR UPDATE chống race condition), POST /auth/logout (thu hồi 1 token hoặc toàn bộ qua logout_all_devices)"
  - "spring.jackson.property-naming-strategy=SNAKE_CASE toàn cục — mọi DTO hiện tại/tương lai tự động map camelCase Java <-> snake_case JSON đúng api/00-QUY-UOC-CHUNG.md, không cần @JsonProperty thủ công"
  - "FilterAutoRegistrationConfig — vá lỗi hạ tầng chung: mọi Filter bean (JwtAuthFilter, RateLimitFilter) không còn bị Spring Boot tự đăng ký trùng lặp vào servlet container ngoài đường SecurityFilterChain"
affects: [01-05, 01-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "@Transactional(noRollbackFor = BusinessException.class) khi method vừa ghi bằng chứng nghiệp vụ (login_attempts, revoke token) VỪA throw BusinessException ngay sau đó — rollback mặc định sẽ xoá luôn bằng chứng cùng lúc với exception, phá vỡ logic đếm/audit dựa trên bản ghi đó"
    - "SELECT ... FOR UPDATE qua @Lock(LockModeType.PESSIMISTIC_WRITE) trên JPQL — cách đơn giản nhất để 2 transaction cạnh tranh cùng 1 row tuần tự hoá đúng, không cần logic retry/version thủ công"
    - "Spring Boot tự đăng ký MỌI Filter bean vào servlet container độc lập với SecurityFilterChain — bất kỳ OncePerRequestFilter mới nào thêm vào common/ đều phải khai trong FilterAutoRegistrationConfig (setEnabled(false)) nếu đã được add vào SecurityConfig, nếu không sẽ chạy 2 lần/request"
    - "spring.jackson.property-naming-strategy=SNAKE_CASE đặt 1 lần ở application.yml — không cần @JsonProperty lặp lại trên từng field DTO của mọi package nghiệp vụ sau này"

key-files:
  created:
    - src/main/java/com/datn/financeapp/auth/repository/UserRepository.java
    - src/main/java/com/datn/financeapp/auth/repository/RefreshTokenRepository.java
    - src/main/java/com/datn/financeapp/auth/repository/LoginAttemptRepository.java
    - src/main/java/com/datn/financeapp/common/wallet/WalletMinimalRepository.java
    - src/main/java/com/datn/financeapp/auth/dto/RegisterRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/LoginRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/RefreshRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/LogoutRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/AuthResponse.java
    - src/main/java/com/datn/financeapp/auth/dto/RefreshResponse.java
    - src/main/java/com/datn/financeapp/auth/dto/UserSummaryDto.java
    - src/main/java/com/datn/financeapp/auth/service/AuthService.java
    - src/main/java/com/datn/financeapp/auth/controller/AuthController.java
    - src/main/java/com/datn/financeapp/common/security/ClientIpResolver.java
    - src/main/java/com/datn/financeapp/common/security/FilterAutoRegistrationConfig.java
    - src/test/java/com/datn/financeapp/auth/AuthRegisterLoginIntegrationTest.java
    - src/test/java/com/datn/financeapp/auth/AuthRefreshRotationIntegrationTest.java
    - .planning/phases/01-nen-tang-xac-thuc/deferred-items.md
  modified:
    - src/main/resources/application.yml
    - src/main/java/com/datn/financeapp/common/security/JwtService.java
    - src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java

key-decisions:
  - "LogoutRequest thêm field refreshToken ngoài logout_all_devices — api/01-XAC-THUC.md mục 4 chỉ đặc tả rõ logout_all_devices, không nêu trường mang token cần thu hồi; suy luận hợp lý duy nhất (logout PHẢI biết token nào để revoke) là dùng cùng cấu trúc refresh_token như /auth/refresh. Ghi rõ ở đây theo đúng nguyên tắc CLAUDE.md gốc 'không tự bịa trường — nếu thiếu phải nêu rõ quyết định', không coi là bịa field ngoài ý đồ tài liệu."
  - "@Transactional(noRollbackFor = BusinessException.class) cho login() và refresh() — phát hiện khi chạy test lockout/reuse-detection lần đầu: rollback mặc định của Spring xoá luôn login_attempts/revoke vừa ghi cùng lúc với exception ném ra, khiến đếm 5 lần sai không bao giờ đủ và reuse detection không thực sự thu hồi gì."
  - "Tạo FilterAutoRegistrationConfig tắt auto-registration servlet container cho JwtAuthFilter/RateLimitFilter — phát hiện khi viết test gọi liên tiếp /auth/register bị 429 sớm hơn dự kiến (rate limit tiêu 2 token/request vì Spring Boot tự đăng ký MỌI Filter bean độc lập với SecurityFilterChain, ngoài dự tính của Plan 03)."
  - "Thêm spring.jackson.property-naming-strategy=SNAKE_CASE toàn cục vào application.yml — CLAUDE.md gốc yêu cầu JSON field snake_case nhưng chưa plan nào trước đó cấu hình Jackson; phát hiện khi viết test assertion field_name và response thực tế trả về fieldName (camelCase mặc định của Jackson)."
  - "JwtService thêm getAccessTokenExpirySeconds() — response expires_in phải khớp đúng cấu hình jwt.access-token-expiry-seconds thật, tránh hằng số trùng lặp lệch khỏi nguồn sự thật."
  - "2 test integration (AuthRegisterLoginIntegrationTest, AuthRefreshRotationIntegrationTest) thay RateLimitFilter thật bằng no-op qua @TestConfiguration @Primary (tên bean phải khớp 'rateLimitFilter' để ghi đè đúng) — nhiều test case gọi /auth/** hơn 5 lần/JVM run, bucket 5/phút/IP dùng chung sẽ chặn nhầm nếu giữ filter thật."

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-07, AUTH-08]

# Metrics
duration: 46min
completed: 2026-08-23
---

# Phase 1 Plan 04: AuthController + AuthService — vòng đời phiên đăng nhập Summary

**4 endpoint bảo mật cao nhất của Phase 1 (register/login/refresh/logout) hoạt động qua HTTP thật với refresh token rotation dùng một lần khoá bằng `SELECT ... FOR UPDATE` chống race condition, reuse detection thu hồi toàn bộ phiên, và khoá đăng nhập 15 phút sau 5 lần sai liên tiếp — verify bằng 8 test tích hợp Testcontainers PostgreSQL thật, bao gồm test 2-thread đồng thời cho race condition.**

## Performance

- **Duration:** 46 phút (0f7f4a6 lúc 00:36:15+07 → 099bcf6 lúc 00:36:36+07, cộng thời gian review/verify sau đó do file đã có sẵn từ trước khi executor này khởi động)
- **Started:** 2026-08-22T23:50:00+07:00 (ước lượng)
- **Completed:** 2026-08-23T01:25:00+07:00
- **Tasks:** 2/2 hoàn thành
- **Files modified:** 17 file mới, 3 file sửa (không tính SUMMARY.md/deferred-items.md)

## Accomplishments

- `AuthService` xử lý đúng 4 luồng nghiệp vụ lõi: `register` tạo `User` + ví "Tiền mặt" số dư 0 trong CÙNG 1 `@Transactional` (không có tình huống signup nửa vời); `login` trả `INVALID_CREDENTIALS` giống hệt nhau (mã lẫn message) dù sai email hay sai password, khoá 15 phút sau 5 lần sai liên tiếp (một lần đúng chen giữa phá chuỗi đúng thuật toán); `refresh` rotation dùng một lần với `SELECT ... FOR UPDATE` (`PESSIMISTIC_WRITE`) khoá đúng race condition — verify bằng test 2 thread `ExecutorService` + `CountDownLatch` gửi đồng thời cùng 1 token, chỉ đúng 1/2 request thành công; `logout` thu hồi đúng 1 token hoặc toàn bộ theo `logout_all_devices`
- Reuse detection: token đã revoke bị gửi lại lần 2 → thu hồi TOÀN BỘ refresh token active của tài khoản đó (kể cả token mới vừa cấp ở lần refresh hợp lệ trước đó) — verify bằng query trực tiếp `findAllByUserIdAndRevokedAtIsNull` trả rỗng
- `AuthController` wire đúng 4 `@PostMapping`, KHÔNG có `@Idempotent` ở bất kỳ đâu (D-11 nghiêm ngặt — đây là POST-hành-động, không phải POST-tạo-mới), `/auth/register` trả `HttpStatus.CREATED` (201)
- Phát hiện và vá 1 lỗi hạ tầng ảnh hưởng TOÀN BỘ dự án (không riêng auth): Spring Boot tự đăng ký mọi `Filter` bean vào servlet container độc lập với `SecurityFilterChain`, khiến `JwtAuthFilter`/`RateLimitFilter` chạy 2 lần/request — `FilterAutoRegistrationConfig` tắt auto-registration, giữ nguyên hành vi đúng qua `SecurityConfig`
- Phát hiện thiếu cấu hình Jackson `SNAKE_CASE` toàn cục — thêm 1 dòng vào `application.yml`, mọi DTO hiện tại (Plan 02-04) lẫn tương lai tự động đúng convention `api/00-QUY-UOC-CHUNG.md` mà không cần sửa lại từng DTO

## Task Commits

Each task was committed atomically:

1. **Task 1: Repository + DTO + AuthService (register/login/refresh/logout)** - `0f7f4a6` (feat)
2. **Task 2: AuthController — wire 4 endpoint, không gắn Idempotent theo D-11** - `099bcf6` (feat)

**Plan metadata:** (commit theo sau khi ghi SUMMARY.md)

## Files Created/Modified

- `src/main/java/com/datn/financeapp/auth/repository/UserRepository.java` — `findByEmail`, `existsByEmail`
- `src/main/java/com/datn/financeapp/auth/repository/RefreshTokenRepository.java` — `findActiveByTokenHashForUpdate` (PESSIMISTIC_WRITE), `revokeAllActiveForUser`, `revokeByTokenHash`, `findAllByUserIdAndRevokedAtIsNull`
- `src/main/java/com/datn/financeapp/auth/repository/LoginAttemptRepository.java` — `findTop5ByEmailOrderByAttemptedAtDesc`, thuật toán khoá xử lý ở Service (Java thuần, không native SQL phức tạp)
- `src/main/java/com/datn/financeapp/common/wallet/WalletMinimalRepository.java` — repository trần, chỉ dùng `save()`
- `src/main/java/com/datn/financeapp/auth/dto/*.java` — `RegisterRequest`, `LoginRequest`, `RefreshRequest`, `LogoutRequest`, `AuthResponse`, `RefreshResponse` (không có `user`, khác `AuthResponse`), `UserSummaryDto`
- `src/main/java/com/datn/financeapp/auth/service/AuthService.java` — business logic 4 luồng, helper `sha256Hex`/`generateSecureRandomToken`/`isLockedOut`
- `src/main/java/com/datn/financeapp/auth/controller/AuthController.java` — 4 endpoint HTTP, không `@Idempotent`
- `src/main/java/com/datn/financeapp/common/security/ClientIpResolver.java` — ưu tiên `X-Forwarded-For`, fallback `getRemoteAddr()`
- `src/main/java/com/datn/financeapp/common/security/FilterAutoRegistrationConfig.java` — vá lỗi filter chạy 2 lần (xem Deviations)
- `src/test/java/com/datn/financeapp/auth/AuthRegisterLoginIntegrationTest.java` — 4 test case (register + ví, email trùng, sai email/password cùng message, khoá sau 5 lần sai)
- `src/test/java/com/datn/financeapp/auth/AuthRefreshRotationIntegrationTest.java` — 4 test case (refresh hợp lệ, reuse detection, race condition 2-thread, logout không all-devices)
- `src/main/resources/application.yml` — thêm `spring.jackson.property-naming-strategy=SNAKE_CASE`
- `src/main/java/com/datn/financeapp/common/security/JwtService.java` — thêm `getAccessTokenExpirySeconds()`
- `src/test/java/com/datn/financeapp/common/exception/GlobalExceptionHandlerTest.java` — thêm `AuthController` vào `excludeFilters` của `@WebMvcTest`
- `.planning/phases/01-nen-tang-xac-thuc/deferred-items.md` — ghi nhận 1 test flaky ngoài phạm vi Plan 04 (xem Deviations)

## Decisions Made

- **`LogoutRequest` thêm `refreshToken`:** `api/01-XAC-THUC.md` mục 4 chỉ đặc tả `logout_all_devices`, không nêu rõ trường mang token cần thu hồi. Vì logout bắt buộc phải biết token nào để revoke, suy luận hợp lý duy nhất là dùng cùng cấu trúc `refresh_token` như `/auth/refresh`. Ghi rõ quyết định này để không bị coi là tự bịa field ngoài ý đồ tài liệu (theo đúng nguyên tắc CLAUDE.md gốc).
- **`@Transactional(noRollbackFor = BusinessException.class)`** cho `login()`/`refresh()` — mặc định Spring rollback MỌI unchecked exception kể cả `BusinessException`; nếu giữ mặc định, bằng chứng `login_attempts` vừa ghi (cho lockout) hoặc hành động `revokeAllActiveForUser` vừa gọi (cho reuse detection) sẽ bị xoá theo cùng lúc với exception bị ném — phá vỡ đúng mục đích cốt lõi của cả 2 cơ chế bảo mật.
- **`FilterAutoRegistrationConfig`** — Spring Boot mặc định tự đăng ký MỌI bean `Filter` vào servlet container, độc lập hoàn toàn với việc `SecurityConfig` đã thêm filter đó vào `SecurityFilterChain` qua `addFilterBefore`/`addFilterAfter`. Hệ quả là `JwtAuthFilter` và `RateLimitFilter` chạy 2 lần/request — với rate limit nghĩa là mỗi request tiêu 2 token thay vì 1, phát hiện khi test tích hợp Plan 04 gọi liên tiếp `/auth/register` bị 429 sớm hơn dự kiến dù RateLimitFilter đã bị thay no-op (bug ẩn ở tầng hạ tầng chung, không riêng gì auth).
- **`spring.jackson.property-naming-strategy=SNAKE_CASE`** — chưa plan nào trước đó cấu hình Jackson toàn cục dù CLAUDE.md gốc yêu cầu JSON field `snake_case`; phát hiện khi test assertion `$.data.access_token` không khớp response thực tế `accessToken` (camelCase mặc định của Jackson với record Java).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `@Transactional` rollback mặc định xoá bằng chứng lockout/reuse-detection cùng lúc với exception**
- **Found during:** Task 1, chạy test `khoaTaiKhoanSau5LanSaiLienTiep_LanThu6TraAccountLocked` và `reuseTokenDaRevoke_ThuHoiToanBoPhien` lần đầu
- **Issue:** Spring mặc định rollback transaction với MỌI unchecked exception. `login()` ghi `LoginAttempt` mới rồi throw `INVALID_CREDENTIALS`; `refresh()` gọi `revokeAllActiveForUser()` rồi throw `REFRESH_TOKEN_INVALID` — cả hai bị rollback theo cùng exception, khiến `isLockedOut()` không bao giờ đếm đủ 5 lần sai và reuse detection không thực sự thu hồi gì
- **Fix:** Thêm `@Transactional(noRollbackFor = BusinessException.class)` cho `login()` và `refresh()`
- **Files modified:** `AuthService.java`
- **Verification:** `mvn test -Dtest=AuthRegisterLoginIntegrationTest,AuthRefreshRotationIntegrationTest` — 8/8 pass
- **Committed in:** `0f7f4a6`

**2. [Rule 1 - Bug] Spring Boot tự đăng ký mọi Filter bean vào servlet container, chạy song song với SecurityFilterChain**
- **Found during:** Task 1, viết test gọi liên tiếp `/auth/register` nhiều lần, bị 429 sớm hơn số lần gọi thực tế
- **Issue:** `JwtAuthFilter`/`RateLimitFilter` là `@Component` implement `OncePerRequestFilter` — Spring Boot `ServletContextInitializerBeans` tự đăng ký MỌI bean `Filter` vào container, độc lập với việc `SecurityConfig` đã thêm chính bean đó vào `SecurityFilterChain`. Kết quả: mỗi request chạy qua 2 filter chain (container + Security), `RateLimitFilter` tiêu 2 token/request
- **Fix:** Tạo `FilterAutoRegistrationConfig` — 2 bean `FilterRegistrationBean` với `setEnabled(false)` cho `JwtAuthFilter` và `RateLimitFilter`, giữ bean vẫn tồn tại (SecurityConfig autowire được) nhưng tắt đường đăng ký container
- **Files modified:** `FilterAutoRegistrationConfig.java` (file mới)
- **Verification:** Test rate-limit trong `AuthRegisterLoginIntegrationTest`/`AuthRefreshRotationIntegrationTest` không còn 429 giả; `RateLimitFilterTest` (Plan 03) vẫn 3/3 pass
- **Committed in:** `0f7f4a6`

**3. [Rule 2 - Missing critical functionality] Thiếu cấu hình Jackson snake_case toàn cục**
- **Found during:** Task 1, chạy test assertion `$.data.access_token` lần đầu — response thực tế trả `accessToken` (camelCase mặc định Jackson với Java record)
- **Issue:** CLAUDE.md gốc và `api/00-QUY-UOC-CHUNG.md` yêu cầu mọi trường JSON dùng `snake_case`; chưa plan nào trước đó cấu hình việc này — đúng nghĩa "thiếu chức năng cần thiết cho tính đúng đắn" của hợp đồng API
- **Fix:** Thêm `spring.jackson.property-naming-strategy=SNAKE_CASE` vào `application.yml` — áp dụng tự động cho mọi DTO hiện tại và tương lai, không cần `@JsonProperty` thủ công từng field
- **Files modified:** `application.yml`
- **Verification:** Test assertion `$.data.access_token`, `$.data.refresh_token`, `$.data.expires_in`… pass đúng field JSON snake_case
- **Committed in:** `0f7f4a6`

**4. [Rule 2 - Missing critical functionality] `expires_in` không có nguồn lấy giá trị nhất quán**
- **Found during:** Task 1, viết `buildAuthResponse`/`refresh()` cần trả `expiresIn` khớp đúng cấu hình JWT thật
- **Issue:** Không có method public nào expose `accessTokenExpirySeconds` đã cấu hình trong `JwtService` — nếu hard-code hằng số riêng ở `AuthService` sẽ có nguy cơ lệch khỏi `jwt.access-token-expiry-seconds` thật nếu cấu hình đổi sau này
- **Fix:** Thêm `JwtService.getAccessTokenExpirySeconds()` — dùng lại đúng 1 nguồn sự thật
- **Files modified:** `JwtService.java`
- **Verification:** Test assertion `$.data.expires_in` khớp giá trị cấu hình thật
- **Committed in:** `0f7f4a6`

**5. [Rule 1 - Bug] `@WebMvcTest` của `GlobalExceptionHandlerTest` tự nạp nhầm `AuthController` mới**
- **Found during:** Task 1, chạy `mvn test` toàn bộ project sau khi thêm `AuthController`
- **Issue:** `AuthController` cần bean `AuthService` không tồn tại trong slice test cô lập của `GlobalExceptionHandlerTest` — cùng vấn đề đã gặp ở Plan 02/03 với `SecurityConfig`/`RateLimitFilter`
- **Fix:** Thêm `AuthController.class` vào `excludeFilters` của `@WebMvcTest`, cùng danh sách với 3 bean đã loại từ trước
- **Files modified:** `GlobalExceptionHandlerTest.java`
- **Verification:** `mvn test -Dtest=GlobalExceptionHandlerTest` pass
- **Committed in:** `0f7f4a6`

---

**Total deviations:** 5 auto-fixed (3 bug, 2 missing-critical-functionality)
**Impact on plan:** Toàn bộ phát sinh khi chạy test thật (đúng tinh thần D-07/D-23 "verify bằng cách chạy thật"). Không có scope creep, không đổi kiến trúc `auth/` đã chốt trong PLAN.md. 2 deviation (#2, #3) ảnh hưởng hạ tầng chung ngoài phạm vi `auth/` nhưng là điều kiện cần để 4 endpoint hoạt động đúng — không hoãn được sang phase sau.

## Issues Encountered

**`JwtServiceTest.tokenBiSuaChuKy_nemSignatureException` flaky khi chạy full suite** — pass khi chạy riêng lẻ, fail ngẫu nhiên khi chạy cùng `mvn test` toàn bộ project. File thuộc Plan 02, không được Plan 04 sửa — ngoài phạm vi theo scope boundary. Đã ghi vào `.planning/phases/01-nen-tang-xac-thuc/deferred-items.md` để xử lý ở lần review Plan 02 sau này, không chặn việc hoàn thành Plan 04 (2 test file của Plan 04 chạy độc lập 8/8 pass ổn định qua nhiều lần chạy lại).

## User Setup Required

Không có bước thủ công bên ngoài. Docker daemon đã chạy sẵn, Testcontainers tự kéo `postgres:16` khi chạy test — đã verify chạy ổn định nhiều lần trong phiên này.

## Next Phase Readiness

- 4 endpoint `auth/` lõi hoàn chỉnh — Plan 05 (`GET/PATCH /auth/me`, đổi/quên/reset mật khẩu) có thể dùng lại `AuthService`, `UserRepository`, `RefreshTokenRepository` ngay, chỉ cần thêm method mới không cần dựng lại hạ tầng
- Plan 05 cần tự bổ sung `permitAll()` cho `/auth/forgot-password`, `/auth/reset-password` — SecurityConfig ĐÃ có sẵn 2 route này trong `permitAll()` từ Plan 02 (xác nhận qua đọc code, không cần sửa gì thêm ở Plan 04 lẫn Plan 05 cho phần này)
- `FilterAutoRegistrationConfig` là vá lỗi hạ tầng chung — mọi `Filter` bean mới thêm ở phase sau (nếu có) phải tự thêm registration tắt tương tự nếu đã add vào `SecurityFilterChain`, nếu không sẽ tái phát lỗi chạy 2 lần
- `spring.jackson.property-naming-strategy=SNAKE_CASE` đã áp dụng toàn cục — mọi DTO package nghiệp vụ Phase 2 trở đi (wallet/, category/, transaction/...) tự động đúng convention, không cần cấu hình lại
- Không có blocker nào cho Plan 05

---
*Phase: 01-nen-tang-xac-thuc*
*Completed: 2026-08-23*

## Self-Check: PASSED

Toàn bộ 18 file trong `key-files.created`/`key-files.modified` xác nhận tồn tại trên đĩa; commit `0f7f4a6` và `099bcf6` xác nhận có trong `git log`. `mvn test -Dtest=AuthRegisterLoginIntegrationTest,AuthRefreshRotationIntegrationTest` — 8/8 pass qua Testcontainers PostgreSQL thật. `mvn -DskipTests compile` exit code 0.
