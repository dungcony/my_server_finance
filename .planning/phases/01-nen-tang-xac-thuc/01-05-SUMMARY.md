---
phase: 01-nen-tang-xac-thuc
plan: 05
subsystem: auth
tags: [spring-security, jwt, password-reset, refresh-token-revoke, testcontainers, bcrypt]

# Dependency graph
requires:
  - phase: 01-04
    provides: "AuthService (register/login/refresh/logout), UserRepository, RefreshTokenRepository.revokeAllActiveForUser(UUID), SecurityConfig permitAll /auth/forgot-password|reset-password, AuthController 4 endpoint đã wire"
provides:
  - "9/9 endpoint api/01-XAC-THUC.md hoạt động qua HTTP thật: GET/PATCH /auth/me, POST /auth/change-password|forgot-password|reset-password"
  - "PasswordResetNotifier (D-22) — interface tách rời + LogPasswordResetNotifier ghi log, sẵn sàng thay SMTP thật không đụng business logic"
  - "AUTH-07 (khoá đăng nhập 15 phút) verify qua HTTP thật (Filter -> Controller -> Service -> DB), khác Plan 04 chỉ test Service layer"
affects: [01-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "changePassword revoke TOÀN BỘ refresh token (không có ngoại lệ 'phiên hiện tại') vì request change-password không mang refresh token nào để giữ lại — quyết định D-24, an toàn hơn đặc tả gốc"
    - "forgotPassword luôn return void ở cả 2 nhánh (email tồn tại/không tồn tại), không throw phân biệt — Controller trả response giống hệt nhau, chống dò danh sách người dùng (T-05-01)"
    - "resetPassword gộp chung lỗi RESET_CODE_INVALID cho cả 3 trường hợp (không tìm thấy/hết hạn/user không tồn tại) — không lộ chi tiết nào cho client"
    - "GET /auth/me dùng JdbcTemplate native COUNT trực tiếp lên bảng transactions/group_members đã có sẵn từ V1/V2 dù chưa có module nghiệp vụ tương ứng — ngoại lệ hợp lý cho Phase 1, Phase 2/5 sẽ thay bằng repository thật"

key-files:
  created:
    - src/main/java/com/datn/financeapp/auth/repository/PasswordResetTokenRepository.java
    - src/main/java/com/datn/financeapp/auth/dto/UpdateProfileRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/ChangePasswordRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/ForgotPasswordRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/ResetPasswordRequest.java
    - src/main/java/com/datn/financeapp/auth/dto/UserDetailDto.java
    - src/main/java/com/datn/financeapp/auth/dto/UserStatsDto.java
    - src/main/java/com/datn/financeapp/auth/service/PasswordResetNotifier.java
    - src/main/java/com/datn/financeapp/auth/service/LogPasswordResetNotifier.java
    - src/test/java/com/datn/financeapp/auth/AuthProfilePasswordIntegrationTest.java
    - src/test/java/com/datn/financeapp/auth/AuthLoginLockoutIntegrationTest.java
  modified:
    - src/main/java/com/datn/financeapp/auth/service/AuthService.java
    - src/main/java/com/datn/financeapp/auth/controller/AuthController.java
    - src/main/java/com/datn/financeapp/common/wallet/WalletMinimalRepository.java

key-decisions:
  - "D-24 áp dụng cho changePassword: 'thu hồi toàn bộ trừ phiên hiện tại' diễn giải thành thu hồi TOÀN BỘ refresh token, vì request /auth/change-password chỉ nhận old_password/new_password (không có refresh token trong body theo api/01) nên không có token nào để giữ lại làm 'phiên hiện tại' — vẫn an toàn hơn đặc tả gốc (access token 1h còn dùng được tới hết hạn tự nhiên, chỉ không refresh được nữa)"
  - "SecurityConfig KHÔNG cần sửa — Plan 02/04 đã permitAll /auth/forgot-password và /auth/reset-password từ trước, xác nhận qua đọc code trước khi bắt đầu"
  - "WalletMinimalRepository thêm countByUserIdAndIsDeletedFalse — method duy nhất mới thêm ngoài save(), phục vụ đúng 1 nhu cầu stats.wallet_count"

requirements-completed: [AUTH-05, AUTH-06, AUTH-07]

# Metrics
duration: 28min
completed: 2026-08-23
---

# Phase 1 Plan 05: Vòng đời tài khoản — profile, đổi/quên/đặt lại mật khẩu, khoá đăng nhập Summary

**5 endpoint cuối cùng của `auth/` (GET/PATCH /auth/me, change-password, forgot-password, reset-password) đóng đủ 9/9 endpoint `api/01-XAC-THUC.md`, cùng `PasswordResetNotifier` tách interface theo D-22 và test tích hợp HTTP thật xác nhận khoá đăng nhập 15 phút sau 5 lần sai liên tiếp (AUTH-07), tự hết hạn theo thời gian, và một lần đăng nhập đúng chen giữa phá đúng chuỗi khoá.**

## Performance

- **Duration:** 28 phút
- **Started:** 2026-08-23T01:32:00Z (ước lượng từ session start)
- **Completed:** 2026-08-23T01:40:00Z
- **Tasks:** 2/2 hoàn thành
- **Files modified:** 11 file mới, 3 file sửa

## Accomplishments

- `AuthService` thêm 5 method nghiệp vụ: `getMe` (stats wallet/transaction/group đếm qua repository + native `COUNT(*)` trên bảng đã có sẵn từ V1/V2), `updateProfile` (PATCH bán phần, field null = không đổi), `changePassword` (thu hồi toàn bộ refresh token, gọi lại `RefreshTokenRepository.revokeAllActiveForUser` có sẵn từ Plan 04 — KHÔNG viết lại logic revoke), `forgotPassword` (cả 2 nhánh return void không throw, chỉ nhánh email tồn tại mới tạo token và gọi notifier), `resetPassword` (mã dùng 1 lần hạn 15 phút, gộp chung lỗi cho mọi trường hợp sai/hết hạn/không tồn tại)
- `PasswordResetNotifier` interface (D-22) + `LogPasswordResetNotifier` implementation duy nhất Phase 1 ghi mã reset ra log — không tích hợp SMTP thật, sẵn sàng thay implementation khác sau này không đụng `AuthService`
- `AuthController` wire đủ 9/9 endpoint `api/01-XAC-THUC.md` — 5 method mới không gắn `@Idempotent` (không đủ điều kiện D-11: GET/PATCH hoặc POST-hành-động, không phải POST-tạo-mới)
- `AuthLoginLockoutIntegrationTest` verify AUTH-07 qua **HTTP thật** (khác Plan 04 vốn test Service layer trực tiếp) — toàn bộ chuỗi `RateLimitFilter` (no-op trong test) → `JwtAuthFilter`/permitAll → `AuthController` → `AuthService` → PostgreSQL thật hoạt động đúng: 5 lần sai liên tiếp → lần 6 dù đúng password vẫn `ACCOUNT_LOCKED`; khoá tự hết hạn sau 15 phút (thao túng `attempted_at` lùi thời gian trực tiếp qua repository, xác nhận `isLockedOut()` tính động không cần cron); 1 lần đăng nhập đúng chen giữa 5 lần sai không đủ điều kiện khoá (allFailed=false)
- `mvn test` toàn bộ project: **33/33 pass, BUILD SUCCESS** — không có regression nào ở 4 plan trước, kể cả `JwtServiceTest` flaky đã ghi trong `deferred-items.md` cũng pass ở lần chạy full suite này (vẫn giữ nguyên trong deferred-items vì bản chất flaky không được sửa, chỉ không biểu hiện lần này)

## Task Commits

Each task was committed atomically:

1. **Task 1: Profile (AUTH-05) + đổi/quên/đặt lại mật khẩu (AUTH-06)** - `5829441` (feat)
2. **Task 2: Wire Controller còn lại + test tích hợp khoá đăng nhập AUTH-07** - `3803f9e` (feat)

**Plan metadata:** (commit theo sau khi ghi SUMMARY.md)

## Files Created/Modified

- `src/main/java/com/datn/financeapp/auth/repository/PasswordResetTokenRepository.java` — `findByTokenHashAndUsedAtIsNull`
- `src/main/java/com/datn/financeapp/auth/dto/UpdateProfileRequest.java` — record chỉ 2 field `fullName`/`avatarUrl`
- `src/main/java/com/datn/financeapp/auth/dto/ChangePasswordRequest.java`, `ForgotPasswordRequest.java`, `ResetPasswordRequest.java` — DTO có validation `@Pattern` khớp mẫu mật khẩu đã dùng ở `RegisterRequest`
- `src/main/java/com/datn/financeapp/auth/dto/UserDetailDto.java`, `UserStatsDto.java` — response `GET /auth/me` kèm `stats` lồng
- `src/main/java/com/datn/financeapp/auth/service/PasswordResetNotifier.java` — interface D-22
- `src/main/java/com/datn/financeapp/auth/service/LogPasswordResetNotifier.java` — `@Component @Slf4j` ghi log
- `src/main/java/com/datn/financeapp/auth/service/AuthService.java` — thêm `getMe`/`updateProfile`/`changePassword`/`forgotPassword`/`resetPassword`, inject thêm `PasswordResetTokenRepository`/`PasswordResetNotifier`/`JdbcTemplate`
- `src/main/java/com/datn/financeapp/auth/controller/AuthController.java` — thêm 5 `@GetMapping`/`@PatchMapping`/`@PostMapping`
- `src/main/java/com/datn/financeapp/common/wallet/WalletMinimalRepository.java` — thêm `countByUserIdAndIsDeletedFalse`
- `src/test/java/com/datn/financeapp/auth/AuthProfilePasswordIntegrationTest.java` — 7 test case gọi trực tiếp `AuthService`
- `src/test/java/com/datn/financeapp/auth/AuthLoginLockoutIntegrationTest.java` — 3 test case qua `MockMvc` HTTP thật

## Decisions Made

- **D-24 (change-password "phiên hiện tại"):** `api/01-XAC-THUC.md` mục 7 chỉ nhận `old_password`/`new_password`, không nhận refresh token trong body. Diễn giải: không có refresh token nào để "giữ lại" nên revoke TOÀN BỘ refresh token của user — vẫn an toàn hơn đặc tả gốc (access token 1h còn dùng được tới hết hạn tự nhiên, người dùng không bị đăng xuất ngay lập tức, chỉ không refresh được nữa). Ghi rõ trong Javadoc `AuthService.changePassword`.
- **SecurityConfig không cần sửa** — xác nhận qua đọc code trước khi bắt đầu: Plan 02/04 đã permitAll `/auth/forgot-password`/`/auth/reset-password` từ trước, đúng như `01-04-SUMMARY.md` mục "Next Phase Readiness" đã ghi.
- **GET /auth/me dùng JdbcTemplate native COUNT** cho `transactions`/`group_members` — Phase 1 chưa có repository nghiệp vụ cho 2 bảng này, dùng trực tiếp COUNT trên bảng schema đã có sẵn từ V1/V2 là ngoại lệ hợp lý (không phải vi phạm kiến trúc), Phase 2/5 sẽ thay bằng repository method thật khi module đó tồn tại.

## Deviations from Plan

Không có — plan thực thi đúng như PLAN.md mô tả. Cả 2 correctness point yêu cầu verify (changePassword gọi `revokeAllActiveForUser`, forgotPassword response giống hệt 2 nhánh) đã implement đúng ngay từ đầu, không cần sửa lại.

## Issues Encountered

Không có blocker nào. Docker daemon chạy sẵn, Testcontainers kéo `postgres:16` ổn định cho cả 2 file test mới.

## User Setup Required

Không có bước thủ công bên ngoài.

## Next Phase Readiness

- `auth/` package hoàn chỉnh 9/9 endpoint — Plan 06 (nếu có nội dung khác của Phase 1, ví dụ closing/verification tổng thể) có thể dựa vào toàn bộ vòng đời tài khoản đã sẵn sàng
- `PasswordResetNotifier` là điểm mở rộng sẵn cho SMTP thật sau này — chỉ cần thêm implementation mới, không đụng `AuthService`
- Không có blocker nào cho Plan 06

---
*Phase: 01-nen-tang-xac-thuc*
*Completed: 2026-08-23*

## Self-Check: PASSED

Toàn bộ 11 file mới trong `key-files.created` xác nhận tồn tại trên đĩa; commit `5829441` và `3803f9e` xác nhận có trong `git log`. `mvn test` toàn bộ project (33 test) — BUILD SUCCESS, 0 failures, 0 errors.
