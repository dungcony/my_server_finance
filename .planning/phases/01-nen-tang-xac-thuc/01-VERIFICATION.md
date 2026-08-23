---
phase: 01-nen-tang-xac-thuc
verified: 2026-08-23T02:25:00Z
status: passed
score: 19/19 requirement IDs xác nhận (CORE-01..11, AUTH-01..08)
overrides_applied: 0
---

# Phase 1: Nền tảng & Xác thực — Báo cáo xác minh

**Mục tiêu phase:** Dựng khung Spring Boot từ số không, tầng `common/` dùng chung (response envelope, exception handling, JWT security, idempotency, rate limiting), và toàn bộ bề mặt xác thực (9 endpoint theo `api/01-XAC-THUC.md`).

**Đã xác minh:** 2026-08-23T02:25:00Z
**Trạng thái:** passed
**Tái xác minh:** Không — lần xác minh đầu tiên (không có `01-VERIFICATION.md` trước đó)

## Đạt được mục tiêu

### Sự thật quan sát được (Observable Truths)

| # | Sự thật | Trạng thái | Bằng chứng |
|---|---------|------------|------------|
| 1 | Project Maven khởi động được, Flyway áp dụng đủ V1→V7 trên PostgreSQL sạch không lỗi | ✓ VERIFIED | `mvn test` chạy `SchemaSmokeTest` — log xác nhận "Successfully applied 7 migrations to schema public, now at version v7"; `mvn -q -DskipTests compile` exit 0 |
| 2 | `ddl-auto=validate` không lỗi cho mọi entity JPA khớp đúng cột thật (kể cả INET, JSONB) | ✓ VERIFIED | `SchemaSmokeTest` context load thành công, không `SchemaManagementException`; entity `LoginAttempt.ipAddress` dùng `@JdbcTypeCode(SqlTypes.INET)`, `IdempotencyKeyEntity.responseBody` dùng `@JdbcTypeCode(SqlTypes.JSON)`, khớp đúng `db/migration/V7__ha_tang_xac_thuc.sql` |
| 3 | README.md đủ để dev mới clone chạy được, đúng trình tự D-01→D-05 | ✓ VERIFIED | `README.md` chứa đủ 4 cụm `.env.example`, `docker compose up`, `mvn spring-boot:run`, `mvn test`; giải thích rõ fail-fast do không có giá trị mặc định |
| 4 | Mọi response tuân thủ khung `{success,data}`/`{success,error}`, lỗi validate trả hết mọi field một lượt | ✓ VERIFIED | `ApiResponse`/`ErrorResponse` record đúng cấu trúc; `GlobalExceptionHandler.handleValidation` dùng `.stream().map(...).toList()` (không `.findFirst()`) — test `GlobalExceptionHandlerTest` xác nhận |
| 5 | JWT access token chỉ chứa `sub`/`plan`/`exp`, không dữ liệu nhạy cảm; ký/verify đúng HS256 chặn algorithm confusion | ✓ VERIFIED | `JwtService.generateAccessToken` chỉ set `subject`/`claim("plan")`/`issuedAt`/`expiration`; `Jwts.parser().verifyWith(key)` (API 0.13.x mới, không `parserBuilder` lỗi thời); test `rejectsDifferentAlgorithm_HS384WithDifferentKey` xác nhận |
| 6 | Idempotency: gọi lại cùng key trong 24h trả đúng response lần đầu, không chạy lại nghiệp vụ; key đang `processing` trả 409 ngay | ✓ VERIFIED | `IdempotencyAspect.handleIdempotent` đúng luồng `INSERT ... ON CONFLICT DO NOTHING` → completed trả cache / processing trả `REQUEST_IN_PROGRESS` 409; `IdempotencyAspectIntegrationTest` (6 test) xác nhận qua Testcontainers |
| 7 | Rate limit 3 tầng (auth 5/phút/IP, ai 30/phút/user, còn lại 120/phút/user), header `X-RateLimit-*` trên mọi response, 429 khớp khung chuẩn | ✓ VERIFIED | `RateLimitProperties.RULES` đúng 3 quota; `RateLimitFilter` set header trên mọi nhánh (kể cả không bị chặn); JSON 429 khớp `{success,error.code,error.message}`; `RateLimitFilterTest` + `AuthIdempotencyRateLimitEndToEndTest` xác nhận qua HTTP thật |
| 8 | Đăng ký tạo user + ví Tiền mặt 0đ trong cùng transaction, sinh access+refresh token | ✓ VERIFIED | `AuthService.register` `@Transactional`, insert `User` rồi `WalletMinimal` (type=cash, initialBalance=0) trong cùng method; test `AuthRegisterLoginIntegrationTest` |
| 9 | Sai email/sai mật khẩu trả cùng `INVALID_CREDENTIALS`; khoá 5 lần sai/15 phút, chen giữa 1 lần đúng phá chuỗi | ✓ VERIFIED | `AuthService.login` không phân nhánh theo lý do sai; `isLockedOut` đúng thuật toán "5 gần nhất, tất cả fail"; `AuthLoginLockoutIntegrationTest` (5 test) bao phủ khoá/hết hạn/chen giữa |
| 10 | Refresh token dùng 1 lần, tái sử dụng thu hồi toàn bộ phiên; 2 request đồng thời chỉ 1 thành công | ✓ VERIFIED | `RefreshTokenRepository.findActiveByTokenHashForUpdate` dùng `PESSIMISTIC_WRITE`; nhánh reuse gọi `revokeAllActiveForUser` trước khi throw; `AuthRefreshRotationIntegrationTest` có test đồng thời |
| 11 | Logout thu hồi đúng token hiện tại; `logout_all_devices=true` thu hồi toàn bộ | ✓ VERIFIED | `AuthService.logout` rẽ nhánh `revokeAllActiveForUser` vs `revokeByTokenHash` đúng theo cờ |
| 12 | `GET /auth/me` trả đủ user + stats; `PATCH /auth/me` chỉ đổi được `full_name`/`avatar_url` | ✓ VERIFIED | `UpdateProfileRequest` chỉ có 2 field, không có `email`/`plan`; `getMe` query `wallet_count`/`transaction_count`/`group_count` thật qua JdbcTemplate |
| 13 | Đổi mật khẩu thu hồi toàn bộ refresh token (khớp `api/01` đã cập nhật, không còn "trừ phiên hiện tại") | ✓ VERIFIED | `AuthService.changePassword` gọi `revokeAllActiveForUser(userId)` không loại trừ; `api/01-XAC-THUC.md` mục 7 đã sửa (commit 8485429) khớp đúng hành vi code |
| 14 | `/auth/forgot-password` luôn trả 200 dù email không tồn tại; mã reset dùng 1 lần, hạn 15 phút | ✓ VERIFIED | `forgotPassword` return sớm không throw khi email không tồn tại; `resetPassword` set `usedAt` cùng transaction đổi mật khẩu, `findByTokenHashAndUsedAtIsNull` chặn dùng lại |
| 15 | CORE-05/06/07/08 không có bề mặt vi phạm ở Phase 1 (chưa có tài nguyên đa người dùng/DELETE/amount) | ✓ VERIFIED | `grep @DeleteMapping` rỗng; `grep amount\|BigDecimal\|double\|float` trong `auth/` rỗng; mọi cột TIMESTAMPTZ map `Instant`, không `LocalDateTime`; `AuthController` không nhận `userId` từ client, luôn qua `SecurityContextUtil.currentUserId()` |
| 16 | Toàn bộ test suite chạy ổn định không flaky | ✓ VERIFIED | `mvn test` chạy trực tiếp (không dùng cache): 35/35 pass, BUILD SUCCESS, bao gồm `JwtServiceTest.tamperedSignature_throwsSignatureException` đã sửa theo cách không phụ thuộc padding base64url |

**Điểm số:** 16/16 sự thật quan sát được đã xác minh (100%)

### Nghệ thuật/Yêu cầu (Requirements Coverage)

| Requirement | Plan nguồn | Mô tả | Trạng thái | Bằng chứng |
|---|---|---|---|---|
| CORE-01 | 01-02 | Khung response thống nhất, validate trả hết field | ✓ SATISFIED | `ApiResponse`/`ErrorResponse`/`GlobalExceptionHandler` |
| CORE-02 | 01-02 | Khung phân trang dùng lại từ Phase 2 | ✓ SATISFIED | `PageMeta`/`PageRequestParams` đã dựng sẵn (chưa có endpoint dùng — đúng phạm vi phase) |
| CORE-03 | 01-03 | Idempotency-Key, nhớ 24h | ✓ SATISFIED | `Idempotent`/`IdempotencyAspect`/`IdempotencyCleanupJob` |
| CORE-04 | 01-03 | Rate limit theo endpoint, header `X-RateLimit-*` | ✓ SATISFIED | `RateLimitFilter`/`RateLimitProperties` |
| CORE-05 | 01-06 | Quyền kiểm tra trong SQL, 404 không phải 403 | ✓ SATISFIED (không có bề mặt vi phạm ở Phase 1) | `AuthController` không nhận `userId` từ client |
| CORE-06 | 01-06 | Xoá mềm, DELETE idempotent | ✓ SATISFIED (không có bề mặt — chưa có DELETE) | `grep @DeleteMapping` rỗng trong `auth/` |
| CORE-07 | 01-06 | Timestamp UTC | ✓ SATISFIED | Mọi cột TIMESTAMPTZ map `Instant` |
| CORE-08 | 01-06 | Số tiền số nguyên VND | ✓ SATISFIED (không có bề mặt — chưa có `amount`) | `grep amount\|BigDecimal` rỗng trong `auth/` |
| CORE-09 | 01-01 | Migration V6 vá lỗi bảo mật | ✓ SATISFIED | V6 đã tồn tại từ trước (D-06), verify chạy sạch qua Flyway |
| CORE-10 | 01-01 | Migration V7 hạ tầng xác thực | ✓ SATISFIED | V7 đã tồn tại, 4 bảng đúng cột, entity khớp |
| CORE-11 | 01-01 | Đối chiếu requirements với schema thật | ✓ SATISFIED | PLAN đọc trực tiếp `V1`/`V7`, không suy đoán |
| AUTH-01 | 01-04 | Đăng ký, ví Tiền mặt, cặp token | ✓ SATISFIED | `AuthService.register` |
| AUTH-02 | 01-04 | Đăng nhập, cùng mã lỗi | ✓ SATISFIED | `AuthService.login` |
| AUTH-03 | 01-04 | Refresh rotation + reuse detection | ✓ SATISFIED | `AuthService.refresh` + `PESSIMISTIC_WRITE` |
| AUTH-04 | 01-04 | Logout thu hồi token | ✓ SATISFIED | `AuthService.logout` |
| AUTH-05 | 01-05 | Xem/sửa hồ sơ, không đổi email/plan | ✓ SATISFIED | `getMe`/`updateProfile` |
| AUTH-06 | 01-05 | Đổi/quên/đặt lại mật khẩu | ✓ SATISFIED | `changePassword`/`forgotPassword`/`resetPassword`, `PasswordResetNotifier` (D-22) |
| AUTH-07 | 01-04, 01-05 | Khoá 5 lần/15 phút, ghi log IP | ✓ SATISFIED | `isLockedOut`, `LoginAttempt` ghi cả thành công/thất bại |
| AUTH-08 | 01-02, 01-04 | JWT access 1h, refresh 30 ngày băm trong DB | ✓ SATISFIED | `JwtService`, `RefreshToken.tokenHash` SHA-256 |

**Không có requirement ID nào orphaned** — toàn bộ 19 ID khai báo trong PLAN frontmatter đều xuất hiện trong REQUIREMENTS.md và có bằng chứng triển khai tương ứng.

### Key Link Verification

| Từ | Đến | Qua | Trạng thái | Chi tiết |
|---|---|---|---|---|
| `application.yml` | `../../db/migration` | `spring.flyway.locations=filesystem:../../db/migration` | ✓ WIRED | Flyway log xác nhận áp dụng 7 migration từ đường dẫn gốc |
| `SchemaSmokeTest` | `application-test.yml` | Testcontainers `@ServiceConnection` | ✓ WIRED | Context load, Flyway tự chạy trên container |
| `GlobalExceptionHandler.handleValidation` | `MethodArgumentNotValidException` | `@ExceptionHandler` convert `error.fields` | ✓ WIRED | Test xác nhận đủ 2 field lỗi |
| `JwtAuthFilter` | `SecurityContextHolder` | `setAuthentication` sau verify | ✓ WIRED | Token hết hạn/sai không set context (chỉ set request attribute) |
| `RateLimitFilter` | `SecurityConfig` filter chain | `addFilterAfter(rateLimitFilter, JwtAuthFilter.class)` | ✓ WIRED | D-18 đúng thứ tự |
| `IdempotencyAspect` | `idempotency_keys` | `INSERT ... ON CONFLICT DO NOTHING` | ✓ WIRED | `IdempotencyTransactionHelper.tryInsertProcessing` |
| `AuthService.register` | `WalletMinimalRepository` | Insert ví Tiền mặt cùng `@Transactional` | ✓ WIRED | Cùng method, cùng transaction |
| `AuthService.refresh` | `RefreshTokenRepository` | `SELECT ... FOR UPDATE` | ✓ WIRED | `findActiveByTokenHashForUpdate` dùng `PESSIMISTIC_WRITE` |
| `AuthController` | `common/idempotency/Idempotent` | KHÔNG gắn lên login/refresh/logout (D-11) | ✓ WIRED (đúng ý đồ) | `grep @Idempotent` chỉ xuất hiện trong Javadoc giải thích, không có annotation thật nào gắn trong `auth/` |
| `AuthService.changePassword` | `RefreshTokenRepository.revokeAllActiveForUser` | Gọi lại method đã có | ✓ WIRED | Không viết lại logic revoke |
| `AuthController.forgotPassword` | `PasswordResetNotifier` | Chỉ gọi khi user tồn tại | ✓ WIRED | Nhánh không tồn tại return sớm |

### Anti-Patterns

Không tìm thấy TODO/FIXME/placeholder/empty-implementation nào trong `src/main/java/`. Không có `return null`/`Optional.empty()` bất thường ngoài các khai báo kiểu hợp lệ.

### Rà soát lại các phát hiện từ Code Review (01-REVIEW.md)

- **WR-01 (IP resolution không nhất quán):** đã dispositioned — người dùng chọn HOÃN sang phase deployment, ghi trong `deferred-items.md`. Không tính là gap ở Phase 1.
- **WR-02 (changePassword thu hồi toàn bộ, không trừ phiên hiện tại):** đã dispositioned — `api/01-XAC-THUC.md` mục 7 được cập nhật khớp đúng hành vi code (commit 8485429 xác nhận tồn tại trong lịch sử git, nội dung file hiện tại khớp).
- **IN-01 (`messages_vi.properties` không dùng):** info-level, không chặn goal — chấp nhận theo review gốc.
- **IN-02 (`avatarUrl` thiếu ràng buộc độ dài):** info-level, không thuộc yêu cầu tường minh của `api/01`.
- **IN-03 (lockout không ép LOWER ở tầng query):** info-level, bất biến ngầm được đảm bảo bởi kỷ luật lowercase nhất quán tại nơi gọi — không phải bug hiện tại.

### Bằng chứng chạy thực tế (không chỉ đọc code tĩnh)

- `mvn -q -DskipTests compile` — exit 0
- `mvn test` (chạy trực tiếp, không dùng kết quả cache) — **35/35 test PASS, BUILD SUCCESS**, bao gồm `SchemaSmokeTest` chạy Flyway V1→V7 thật trên Testcontainers PostgreSQL 16
- `git status --short` sạch — không có file chưa commit, `.env` không lọt vào git, `.env.example` có đủ biến mẫu
- Schema drift check (`gsd-sdk query verify.schema-drift 01`) — valid, 0 issues (đã xác nhận trước khi vào phiên này)

## Kết luận

Toàn bộ 16 sự thật quan sát được và 19 requirement ID (CORE-01 đến CORE-11, AUTH-01 đến AUTH-08) đều có bằng chứng triển khai thật trong codebase, được xác nhận bằng cả đọc mã nguồn lẫn chạy test thực (không chỉ tin SUMMARY.md). Không phát hiện gap mới. Hai warning từ code review đều đã được dispositioned rõ ràng (một hoãn có chủ đích, một do tài liệu API vừa được sửa khớp code). Không có mục nào cần xác minh thủ công bởi con người — toàn bộ hành vi (rotation, lockout, idempotency, rate limit, response envelope) đều có test tích hợp trên PostgreSQL thật qua Testcontainers.

**Phase 1 đạt mục tiêu đề ra. Sẵn sàng chuyển sang Phase 2.**

---

_Đã xác minh: 2026-08-23T02:25:00Z_
_Verifier: Claude (gsd-verifier)_
